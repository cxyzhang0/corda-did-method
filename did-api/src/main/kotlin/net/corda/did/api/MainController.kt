/**
 * Persistent code
 *
 */

package net.corda.did.api



import com.natpryce.onFailure
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.services.vault.builder
import net.corda.core.utilities.getOrThrow
import net.corda.did.state.DidState
import org.springframework.http.MediaType
import org.springframework.http.MediaType.*
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity
import net.corda.core.utilities.loggerFor
import net.corda.did.flows.CreateDidFlow
import net.corda.did.flows.DeleteDidFlow
import net.corda.did.state.DidStatus
import net.corda.did.flows.UpdateDidFlow
import org.springframework.web.context.request.async.DeferredResult
import java.util.concurrent.Executors

/**
 *  A Spring Boot Server API controller for interacting with the node via RPC.
 */

@RestController
@RequestMapping("/")
// ??? moritzplatt 2019-06-20 -- consider passing connection parameters instead so the controller can manage the connection
// itself. i.e. re-establishing connection in case it fails or adding functionality to re-establish a connection in case of errors

// pranav 2019-06-25 added RPC reconnection library to reconnect if node connection is lost https://docs.corda.net/clientrpc.html#reconnecting-rpc-clients
class MainController(rpc: NodeRPCConnection) {

    companion object {
         val logger = loggerFor<MainController>()

    }
    private val proxy = rpc.proxy
    private val queryUtils = QueryUtil(proxy)
    private val apiUtils = APIUtils()

    private val executorService = Executors.newSingleThreadExecutor()
    /**
     * Create DID
     */
    @PutMapping(value = "{did}",
            produces = arrayOf(MediaType.APPLICATION_JSON_VALUE), consumes = arrayOf(MediaType.MULTIPART_FORM_DATA_VALUE))
    fun createDID( @PathVariable(value = "did") did: String, @RequestPart("instruction") instruction: String, @RequestPart("document") document: String ) : DeferredResult<ResponseEntity<Any?>> {
        val apiResult = DeferredResult<ResponseEntity<Any?>>()
        try {

            logger.info( "inside create function" )
            val envelope = apiUtils.generateEnvelope(instruction,document,did)
            val uuid = net.corda.did.CordaDid(did).uuid

            // ??? moritzplatt 2019-06-20 -- suggestion here would be to remove this block and instead of querying, rely on the output of the startFlowDynamic call only
            // the current implementation introduces a race condition between the `getDIDDocumentByLinearId` call and the
            // consumption of the `returnValue`
            val didJson = queryUtils.getDIDDocumentByLinearId( uuid.toString() )
            if( !didJson.isEmpty() ){
                    logger.info("conflict occurred")
                    apiResult.setErrorResult(ResponseEntity ( ApiResponse( APIMessage.CONFLICT ).toResponseObj(), HttpStatus.CONFLICT ))
                    return apiResult
            }
            /**
            * Validate envelope
            */
            val envelopeVerified = envelope.validateCreation()
            envelopeVerified.onFailure { apiResult.setErrorResult( apiUtils.sendErrorResponse( it.reason ));return apiResult}
            logger.info( "document id" + uuid )
            // ??? moritzplatt 2019-06-20 -- as described in comments on the flow logic, this should not be passed from the API
            val originator = proxy.nodeInfo().legalIdentities.first()

            /* WIP :Need clarification from Moritz on how the witnesses can be fetched*/

            // ??? moritzplatt 2019-06-20 -- the API should not be aware of the witnesses. The CorDapp should be aware
            // of the set of witnesses by configuration. Considering all network members witnesses is incorrect.
            val witnessNodes = proxy.networkMapSnapshot().flatMap { it.legalIdentities }.toSet()

                // ??? moritzplatt 2019-06-20 -- consider comments on the flow constructor
                val didState = DidState(envelope, originator, witnessNodes.minus( proxy.nodeInfo().legalIdentities.toSet() ), DidStatus.VALID, UniqueIdentifier.fromString(uuid.toString()))
                val flowHandler = proxy.startFlowDynamic(CreateDidFlow::class.java, didState)

                // ??? moritzplatt 2019-06-20 -- not familiar with Spring but `getOrThrow` is blocking.
                // Maybe there is a pattern around futures (i.e. https://www.baeldung.com/spring-async)?
                // Just a thought though
                executorService.submit {
                    try {
                        val result = flowHandler.use { it.returnValue.getOrThrow() }
                        apiResult.setResult(ResponseEntity.ok().body(ApiResponse(result.toString()).toResponseObj()))
                    }
                    catch(e: IllegalArgumentException ){
                        apiResult.setErrorResult(ResponseEntity.badRequest().body( ApiResponse(e.message).toResponseObj() ))

                    }
                    catch( e : DIDDeletedException ){
                        logger.info("provided DID already exists and is deleted")
                        apiResult.setErrorResult(ResponseEntity ( ApiResponse( APIMessage.DID_DELETED ).toResponseObj(), HttpStatus.CONFLICT))
                    }

                }

                return apiResult


        }
        catch( e : Exception ){
            logger.error( e.message )
            apiResult.setErrorResult(ResponseEntity.badRequest().body( ApiResponse(e.message).toResponseObj() ))
            return apiResult
        }


    }
/*
* Fetch DID document
* */
    @GetMapping("{did}", produces = [APPLICATION_JSON_VALUE])
    fun fetchDIDDocument( @PathVariable(value = "did") did: String ):ResponseEntity<Any?> {
        logger.info("Checking criteria against the Vault")
        try {
            val uuid = net.corda.did.CordaDid(did).uuid
            builder {
                val didJson = queryUtils.getDIDDocumentByLinearId(uuid.toString())
                if( didJson.isEmpty() ){
                    val response = ApiResponse( APIMessage.NOT_FOUND )
                    return ResponseEntity( response.toResponseObj(), HttpStatus.NOT_FOUND )

                }
                // ??? moritzplatt 2019-06-20 -- do not return the re-serialised version based on JsonObject. Signatures may not match
                // pranav 2019-06-25 updated code to return raw document as a string
                return ResponseEntity.ok().body(didJson)
            }
        }
        catch ( e : IllegalArgumentException ){
            logger.error( e.toString())
            return ResponseEntity.status( HttpStatus.BAD_REQUEST ).body( ApiResponse( APIMessage.INCORRECT_FORMAT ).toResponseObj() )

        }
        catch( e : DIDDeletedException ){
            logger.info("DID no longer exists")
            return ResponseEntity ( ApiResponse( APIMessage.DID_DELETED ).toResponseObj(), HttpStatus.NOT_FOUND )
        }
        catch ( e : Exception ){
            logger.error( e.toString() )
            return ResponseEntity.status( HttpStatus.INTERNAL_SERVER_ERROR ).body( ApiResponse( e.message ).toResponseObj() )
        }
    }

    @PostMapping(value = "{did}",
            produces = arrayOf(MediaType.APPLICATION_JSON_VALUE), consumes=arrayOf(MediaType.MULTIPART_FORM_DATA_VALUE))
    fun updateDID( @PathVariable(value = "did") did: String, @RequestParam("instruction") instruction: String, @RequestParam("document") document: String ) : DeferredResult<ResponseEntity<Any?>> {
        val apiResult = DeferredResult<ResponseEntity<Any?>>()
        try {
            val envelope = apiUtils.generateEnvelope(instruction,document,did)
            val uuid = net.corda.did.CordaDid(did).uuid

            // ??? moritzplatt 2019-06-20 -- merge with assignment var didJson = try { ... }
            try {
                val didJson = queryUtils.getCompleteDIDDocumentByLinearId(uuid.toString())
                val envelopeVerified = envelope.validateModification( didJson )
                envelopeVerified.onFailure {apiResult.setErrorResult( apiUtils.sendErrorResponse( it.reason ));return apiResult }
            }
            catch( e : NullPointerException ){
                apiResult.setErrorResult(ResponseEntity ( ApiResponse( APIMessage.NOT_FOUND ).toResponseObj(), HttpStatus.NOT_FOUND ))
                return apiResult
            }
            catch( e : DIDDeletedException ){
                apiResult.setErrorResult( ResponseEntity ( ApiResponse( APIMessage.DID_DELETED ).toResponseObj(), HttpStatus.NOT_FOUND ))
                return apiResult
            }

            /**
             * Validate envelope
             */



            logger.info("document id" + uuid)
            // ??? moritzplatt 2019-06-20 -- should be done in flow
            val originator = proxy.nodeInfo().legalIdentities.first()


            /* WIP :Need clarification from Moritz on how the witnesses can be fetched*/

            // ??? moritzplatt 2019-06-20 -- should be done in flow
            val witnessNodes = proxy.networkMapSnapshot().flatMap { it.legalIdentities }.toSet()

            logger.info( "creating the corda state object" )
            // ??? moritzplatt 2019-06-20 -- should be done in flow
            val didState = DidState(envelope, originator, witnessNodes.minus(proxy.nodeInfo().legalIdentities.toSet() ), DidStatus.VALID , UniqueIdentifier.fromString(uuid.toString()) )
            logger.info( "invoking the flow" )

            val flowHandler = proxy.startFlowDynamic(UpdateDidFlow::class.java, didState)
            logger.info( "get result from the flow" )
            //start
            executorService.submit {
                try {
                    val result = flowHandler.use { it.returnValue.getOrThrow() }
                    logger.info( "flow successful" )
                    apiResult.setResult(ResponseEntity.ok().body( ApiResponse(result.toString()).toResponseObj() ))
                }
                catch(e: IllegalArgumentException ){
                    apiResult.setErrorResult( ResponseEntity.badRequest().body( ApiResponse(e.message).toResponseObj() ))

                }
                catch( e : DIDDeletedException ){
                    logger.info("provided DID already exists and is deleted")
                    apiResult.setErrorResult(ResponseEntity ( ApiResponse( APIMessage.DID_DELETED ).toResponseObj(), HttpStatus.CONFLICT))
                }

            }
            //end


            return apiResult

        }
        catch( e : Exception ){
            logger.error( e.message )
            apiResult.setErrorResult(ResponseEntity.badRequest().body( ApiResponse( e.message ).toResponseObj() ))
            return apiResult
        }
    }


    // ??? moritzplatt 2019-06-20 -- does a `DELETE` require a document at all? it could be an instruction set only?
    @DeleteMapping(value = "{did}",
            produces = arrayOf(MediaType.APPLICATION_JSON_VALUE), consumes=arrayOf(MediaType.MULTIPART_FORM_DATA_VALUE))
    fun deleteDID( @PathVariable(value = "did") did: String, @RequestPart("instruction") instruction: String, @RequestPart("document") document: String ) : DeferredResult<ResponseEntity<Any?>> {
        val apiResult = DeferredResult<ResponseEntity<Any?>>()
        try {

            // ??? moritzplatt 2019-06-20 -- consider factoring these checks out to a generic method
            val envelope = apiUtils.generateEnvelope(instruction,document,did)
            val uuid = net.corda.did.CordaDid(did).uuid
            // ??? moritzplatt 2019-06-20 -- variable naming
            // ??? moritzplatt 2019-06-20 -- merge assignment with try block

            try {
                val didJson = queryUtils.getCompleteDIDDocumentByLinearId(uuid.toString())
                val envelopeVerified = envelope.validateModification( didJson )
                envelopeVerified.onFailure { apiResult.setErrorResult( apiUtils.sendErrorResponse(it.reason) );return apiResult }
            }
            catch( e : NullPointerException ){
                apiResult.setErrorResult( ResponseEntity ( ApiResponse( APIMessage.NOT_FOUND ).toResponseObj(), HttpStatus.NOT_FOUND ))
                return apiResult
            }
            catch( e : DIDDeletedException ){
                apiResult.setErrorResult( ResponseEntity ( ApiResponse( APIMessage.DID_DELETED ).toResponseObj(), HttpStatus.NOT_FOUND ) )
                return apiResult
            }

            /**
             * Validate envelope
             */


            logger.info("document id" + uuid)
            // ??? moritzplatt 2019-06-20 -- in flow
            val originator = proxy.nodeInfo().legalIdentities.first()

            /* WIP :Need clarification from Moritz on how the witnesses can be fetched*/

            // ??? moritzplatt 2019-06-20 -- see comment above (config in CorDapp)
            val witnessNodes = proxy.networkMapSnapshot().flatMap { it.legalIdentities }.toSet()


            logger.info( "creating the corda state object" )
            // ??? moritzplatt 2019-06-20 -- assemble this in flow
            val didState = DidState( envelope, originator, witnessNodes.minus( proxy.nodeInfo().legalIdentities.toSet() ), DidStatus.DELETED, UniqueIdentifier.fromString(uuid.toString()))
            logger.info( "invoking the flow" )
            val flowHandler = proxy.startFlowDynamic( DeleteDidFlow::class.java, didState)
            logger.info( "get result from the flow" )
            executorService.submit {
                try {
                    val result = flowHandler.use { it.returnValue.getOrThrow() }
                    logger.info( "flow successful" )
                    apiResult.setResult(ResponseEntity.ok().body( ApiResponse(result.toString()).toResponseObj() ))
                }
                catch(e: IllegalArgumentException ){
                    apiResult.setErrorResult( ResponseEntity.badRequest().body( ApiResponse(e.message).toResponseObj() ))

                }
                catch( e : DIDDeletedException ){
                    logger.info("provided DID already exists and is deleted")
                    apiResult.setErrorResult(ResponseEntity ( ApiResponse( APIMessage.DID_DELETED ).toResponseObj(), HttpStatus.CONFLICT))
                }

            }

            return apiResult

        }
        catch( e : Exception ){
            logger.error( e.message )
            apiResult.setErrorResult( ResponseEntity.badRequest().body( ApiResponse( e.message ).toResponseObj() ))
            return apiResult
        }
    }
}
