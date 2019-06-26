package net.corda.did.api
import net.corda.core.crypto.sign
import net.corda.core.utilities.toBase58
import net.corda.did.CryptoSuite
import net.i2p.crypto.eddsa.KeyPairGenerator
import org.junit.Before
import org.junit.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.net.URI
import java.util.*
import java.io.FileInputStream
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
/**
 * Persistent code
 *
 */

class UpdateDIDAPITest {
    lateinit var mockMvc: MockMvc
    lateinit var mainController: MainController
    lateinit var apiUrl: String

    @Before
    fun setup() {
        val prop = Properties()
        prop.load(FileInputStream(System.getProperty("user.dir") + "/config.properties"))
        apiUrl = prop.getProperty("apiUrl")
        val rpcHost = prop.getProperty("rpcHost")
        val rpcPort = prop.getProperty("rpcPort")
        val username = prop.getProperty("username")
        val password = prop.getProperty("password")
        val rpc = NodeRPCConnection(rpcHost, username, password, rpcPort.toInt())
        rpc.initialiseNodeRPCConnection()
        mainController = MainController(rpc)
        mockMvc = MockMvcBuilders.standaloneSetup(mainController).build()
    }

    @Test
    fun `Create a DID and update the document with new public key` () {
        val kp = KeyPairGenerator().generateKeyPair()

        val pub = kp.public.encoded.toBase58()

        val uuid = UUID.randomUUID()

        val documentId = "did:corda:tcn:"+uuid

        val uri = URI("${documentId}#keys-1")

        val document = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1 = kp.private.sign(document.toByteArray(Charsets.UTF_8))

        val encodedSignature1 = signature1.bytes.toBase58()

        val instruction = """{
		|  "action": "create",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1"
		|	}
		|  ]
		|}""".trimMargin()
        val instructionjsonFile = MockMultipartFile("instruction", "", "application/json", instruction.toByteArray())
        val documentjsonFile = MockMultipartFile("document", "", "application/json", document.toByteArray())
        val builder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).file(instructionjsonFile).file(documentjsonFile).with { request ->
            request.method = "PUT"
            request
        }
        mockMvc.perform(builder).andExpect(MockMvcResultMatchers.status().isOk()).andReturn()


        /* update test*/
        val kpNew = KeyPairGenerator().generateKeyPair()

        val pubNew = kpNew.public.encoded.toBase58()

        val uriNew = URI("${documentId}#keys-2")

        val documentNew = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
        |  "created": "1970-01-01T00:00:00Z",
		|  "updated": "1970-01-02T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	},
        | {
		|	  "id": "$uriNew",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pubNew"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1New = kp.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val signature2New = kpNew.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val encodedSignature1New = signature1New.bytes.toBase58()
        val encodedSignature2New = signature2New.bytes.toBase58()
        val instructionNew = """{
		|  "action": "update",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1New"
		|	},
		|	{
		|	  "id": "$uriNew",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature2New"
		|	}
		|  ]
		|}""".trimMargin()
        val updateBuilder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).param("instruction",instructionNew).param("document",documentNew)
        mockMvc.perform(updateBuilder).andExpect(MockMvcResultMatchers.status().isOk())
        mockMvc.perform(MockMvcRequestBuilders.get(apiUrl+"did:corda:tcn:"+uuid.toString())).andExpect(MockMvcResultMatchers.status().isOk()).andExpect(MockMvcResultMatchers.content().json(documentNew)).andReturn()



    }


    @Test
    fun `Update document by using single private key to sign multiple public keys should fail` () {
        val kp = KeyPairGenerator().generateKeyPair()

        val pub = kp.public.encoded.toBase58()

        val uuid = UUID.randomUUID()

        val documentId = "did:corda:tcn:"+uuid

        val uri = URI("${documentId}#keys-1")

        val document = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1 = kp.private.sign(document.toByteArray(Charsets.UTF_8))

        val encodedSignature1 = signature1.bytes.toBase58()

        val instruction = """{
		|  "action": "create",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1"
		|	}
		|  ]
		|}""".trimMargin()
        val instructionjsonFile = MockMultipartFile("instruction", "", "application/json", instruction.toByteArray())
        val documentjsonFile = MockMultipartFile("document", "", "application/json", document.toByteArray())
        val builder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).file(instructionjsonFile).file(documentjsonFile).with { request ->
            request.method = "PUT"
            request
        }
        mockMvc.perform(builder).andExpect(MockMvcResultMatchers.status().isOk()).andReturn()


        /* update test*/
        val kpNew = KeyPairGenerator().generateKeyPair()

        val pubNew = kpNew.public.encoded.toBase58()

        val uriNew = URI("${documentId}#keys-2")

        val documentNew = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	},
        | {
		|	  "id": "$uriNew",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pubNew"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1New = kp.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val signature2New = kp.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val encodedSignature1New = signature1New.bytes.toBase58()
        val encodedSignature2New = signature2New.bytes.toBase58()
        val instructionNew = """{
		|  "action": "update",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1New"
		|	},
		|	{
		|	  "id": "$uriNew",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature2New"
		|	}
		|  ]
		|}""".trimMargin()
        val updateBuilder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).param("instruction",instructionNew).param("document",documentNew)
        mockMvc.perform(updateBuilder).andExpect(MockMvcResultMatchers.status().is4xxClientError())



    }

    @Test
    fun `Updating original public keys of a document should fail` () {
        val kp = KeyPairGenerator().generateKeyPair()

        val pub = kp.public.encoded.toBase58()

        val uuid = UUID.randomUUID()

        val documentId = "did:corda:tcn:"+uuid

        val uri = URI("${documentId}#keys-1")

        val document = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1 = kp.private.sign(document.toByteArray(Charsets.UTF_8))

        val encodedSignature1 = signature1.bytes.toBase58()

        val instruction = """{
		|  "action": "create",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1"
		|	}
		|  ]
		|}""".trimMargin()
        val instructionjsonFile = MockMultipartFile("instruction", "", "application/json", instruction.toByteArray())
        val documentjsonFile = MockMultipartFile("document", "", "application/json", document.toByteArray())
        val builder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).file(instructionjsonFile).file(documentjsonFile).with { request ->
            request.method = "PUT"
            request
        }
        mockMvc.perform(builder).andExpect(MockMvcResultMatchers.status().isOk()).andReturn()


        /* update test*/
        val kpNew = KeyPairGenerator().generateKeyPair()

        val pubNew = kpNew.public.encoded.toBase58()

        val uriNew = URI("${documentId}#keys-2")

        val documentNew = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pubNew"
		|	},
        | {
		|	  "id": "$uriNew",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pubNew"
		|	}
		|  ]
		|}""".trimMargin()
        val signature2New = kpNew.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val encodedSignature2New = signature2New.bytes.toBase58()
        val instructionNew = """{
		|  "action": "update",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature2New"
		|	},
		|	{
		|	  "id": "$uriNew",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature2New"
		|	}
		|  ]
		|}""".trimMargin()
        val updateBuilder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).param("instruction",instructionNew).param("document",documentNew)
        mockMvc.perform(updateBuilder).andExpect(MockMvcResultMatchers.status().is4xxClientError())



    }


    @Test
    fun `Calling an update operation with create command should fail` () {
        val kp = KeyPairGenerator().generateKeyPair()

        val pub = kp.public.encoded.toBase58()

        val uuid = UUID.randomUUID()

        val documentId = "did:corda:tcn:"+uuid

        val uri = URI("${documentId}#keys-1")

        val document = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1 = kp.private.sign(document.toByteArray(Charsets.UTF_8))

        val encodedSignature1 = signature1.bytes.toBase58()

        val instruction = """{
		|  "action": "create",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1"
		|	}
		|  ]
		|}""".trimMargin()
        val instructionjsonFile = MockMultipartFile("instruction", "", "application/json", instruction.toByteArray())
        val documentjsonFile = MockMultipartFile("document", "", "application/json", document.toByteArray())
        val builder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).file(instructionjsonFile).file(documentjsonFile).with { request ->
            request.method = "PUT"
            request
        }
        mockMvc.perform(builder).andExpect(MockMvcResultMatchers.status().isOk()).andReturn()


        /* update test*/
        val kpNew = KeyPairGenerator().generateKeyPair()

        val pubNew = kpNew.public.encoded.toBase58()

        val uriNew = URI("${documentId}#keys-2")

        val documentNew = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	},
        | {
		|	  "id": "$uriNew",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pubNew"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1New = kp.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val signature2New = kpNew.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val encodedSignature1New = signature1New.bytes.toBase58()
        val encodedSignature2New = signature2New.bytes.toBase58()
        val instructionNew = """{
		|  "action": "create",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1New"
		|	},
		|	{
		|	  "id": "$uriNew",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature2New"
		|	}
		|  ]
		|}""".trimMargin()
        val updateBuilder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).param("instruction",instructionNew).param("document",documentNew)
        mockMvc.perform(updateBuilder).andExpect(MockMvcResultMatchers.status().is4xxClientError())



    }


    @Test
    fun `Update should fail for incorrect document format` () {
        val kp = KeyPairGenerator().generateKeyPair()

        val pub = kp.public.encoded.toBase58()

        val uuid = UUID.randomUUID()

        val documentId = "did:corda:tcn:"+uuid

        val uri = URI("${documentId}#keys-1")

        val document = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "created": "1970-01-01T00:00:00Z",
        |  "id": "${documentId}",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1 = kp.private.sign(document.toByteArray(Charsets.UTF_8))

        val encodedSignature1 = signature1.bytes.toBase58()

        val instruction = """{
		|  "action": "create",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1"
		|	}
		|  ]
		|}""".trimMargin()
        val instructionjsonFile = MockMultipartFile("instruction", "", "application/json", instruction.toByteArray())
        val documentjsonFile = MockMultipartFile("document", "", "application/json", document.toByteArray())
        val builder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).file(instructionjsonFile).file(documentjsonFile).with { request ->
            request.method = "PUT"
            request
        }
        mockMvc.perform(builder).andExpect(MockMvcResultMatchers.status().isOk()).andReturn()


        /* update test*/
        val kpNew = KeyPairGenerator().generateKeyPair()

        val pubNew = kpNew.public.encoded.toBase58()

        val uriNew = URI("${documentId}#keys-2")

        val documentNew = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	},
        | {
		|	  "id": "$uriNew",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pubNew"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1New = kp.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val signature2New = kpNew.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val encodedSignature1New = signature1New.bytes.toBase58()
        val encodedSignature2New = signature2New.bytes.toBase58()
        val instructionNew = """{
		|  "action": "update",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1New"
		|	},
		|	{
		|	  "id": "$uriNew",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature2New"
		|	}
		|  ]
		|}""".trimMargin()
        val updateBuilder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).param("instruction",instructionNew).param("document",documentNew)
        mockMvc.perform(updateBuilder).andExpect(MockMvcResultMatchers.status().is4xxClientError())



    }
    @Test
    fun `Update should fail if instruction format is incorrect` () {
        val kp = KeyPairGenerator().generateKeyPair()

        val pub = kp.public.encoded.toBase58()

        val uuid = UUID.randomUUID()

        val documentId="did:corda:tcn:"+uuid

        val uri = URI("${documentId}#keys-1")

        val document = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1 = kp.private.sign(document.toByteArray(Charsets.UTF_8))

        val encodedSignature1 = signature1.bytes.toBase58()

        val instruction = """{
		|  "action": "create",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1"
		|	}
		|  ]
		|}""".trimMargin()
        val instructionjsonFile = MockMultipartFile("instruction", "", "application/json", instruction.toByteArray())
        val documentjsonFile = MockMultipartFile("document", "", "application/json", document.toByteArray())
        val builder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).file(instructionjsonFile).file(documentjsonFile).with { request ->
            request.method = "PUT"
            request
        }
        mockMvc.perform(builder).andExpect(MockMvcResultMatchers.status().isOk()).andReturn()


        /* update test*/
        val kpNew = KeyPairGenerator().generateKeyPair()

        val pubNew = kpNew.public.encoded.toBase58()

        val uriNew = URI("${documentId}#keys-2")

        val documentNew = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	},
        | {
		|	  "id": "$uriNew",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pubNew"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1New = kp.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val signature2New = kpNew.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val encodedSignature1New = signature1New.bytes.toBase58()
        val encodedSignature2New = signature2New.bytes.toBase58()
        val instructionNew = """{
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1New"
		|	},
		|	{
		|	  "id": "$uriNew",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature2New"
		|	}
		|  ]
		|}""".trimMargin()
        val updateBuilder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).param("instruction",instructionNew).param("document",documentNew)
        mockMvc.perform(updateBuilder).andExpect(MockMvcResultMatchers.status().is4xxClientError())




    }


    @Test
    fun `Create a DID and update the document with missing public key` () {
        val kp = KeyPairGenerator().generateKeyPair()

        val pub = kp.public.encoded.toBase58()

        val uuid = UUID.randomUUID()

        val documentId="did:corda:tcn:"+uuid

        val uri = URI("${documentId}#keys-1")

        val document = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1 = kp.private.sign(document.toByteArray(Charsets.UTF_8))

        val encodedSignature1 = signature1.bytes.toBase58()

        val instruction = """{
		|  "action": "create",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1"
		|	}
		|  ]
		|}""".trimMargin()
        val instructionjsonFile = MockMultipartFile("instruction", "", "application/json", instruction.toByteArray())
        val documentjsonFile = MockMultipartFile("document", "", "application/json", document.toByteArray())
        val builder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).file(instructionjsonFile).file(documentjsonFile).with { request ->
            request.method = "PUT"
            request
        }
        mockMvc.perform(builder).andExpect(MockMvcResultMatchers.status().isOk()).andReturn()


        /* update test*/
        val kpNew = KeyPairGenerator().generateKeyPair()


        val uriNew = URI("${documentId}#keys-2")

        val documentNew = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	},
        | {
		|	  "id": "$uriNew",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1New = kp.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val signature2New = kpNew.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val encodedSignature1New = signature1New.bytes.toBase58()
        val encodedSignature2New = signature2New.bytes.toBase58()
        val instructionNew = """{
		|  "action": "update",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1New"
		|	},
		|	{
		|	  "id": "$uriNew",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature2New"
		|	}
		|  ]
		|}""".trimMargin()
        val updateBuilder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).param("instruction",instructionNew).param("document",documentNew)
        mockMvc.perform(updateBuilder).andExpect(MockMvcResultMatchers.status().is4xxClientError())



    }

    @Test
    fun `Create a DID and update the document with missing signature` () {
        val kp = KeyPairGenerator().generateKeyPair()

        val pub = kp.public.encoded.toBase58()

        val uuid = UUID.randomUUID()

        val documentId="did:corda:tcn:"+uuid

        val uri = URI("${documentId}#keys-1")

        val document = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1 = kp.private.sign(document.toByteArray(Charsets.UTF_8))

        val encodedSignature1 = signature1.bytes.toBase58()

        val instruction = """{
		|  "action": "create",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1"
		|	}
		|  ]
		|}""".trimMargin()
        val instructionjsonFile = MockMultipartFile("instruction", "", "application/json", instruction.toByteArray())
        val documentjsonFile = MockMultipartFile("document", "", "application/json", document.toByteArray())
        val builder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).file(instructionjsonFile).file(documentjsonFile).with { request ->
            request.method = "PUT"
            request
        }
        mockMvc.perform(builder).andExpect(MockMvcResultMatchers.status().isOk()).andReturn()


        /* update test*/
        val kpNew = KeyPairGenerator().generateKeyPair()

        val pubNew = kpNew.public.encoded.toBase58()

        val uriNew = URI("${documentId}#keys-2")

        val documentNew = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	},
        | {
		|	  "id": "$uriNew",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pubNew"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1New = kp.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val encodedSignature1New = signature1New.bytes.toBase58()
        val instructionNew = """{
		|  "action": "update",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1New"
		|	},
		|	{
		|	  "id": "$uriNew",
		|	  "type": "Ed25519Signature2018"
		|	}
		|  ]
		|}""".trimMargin()
        val updateBuilder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).param("instruction",instructionNew).param("document",documentNew)
        mockMvc.perform(updateBuilder).andExpect(MockMvcResultMatchers.status().is4xxClientError())



    }

    @Test
    fun `Update the document with all new public keys should fail` () {
        val kp = KeyPairGenerator().generateKeyPair()

        val pub = kp.public.encoded.toBase58()

        val uuid = UUID.randomUUID()

        val documentId = "did:corda:tcn:"+uuid

        val uri = URI("${documentId}#keys-1")

        val document = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1 = kp.private.sign(document.toByteArray(Charsets.UTF_8))

        val encodedSignature1 = signature1.bytes.toBase58()

        val instruction = """{
		|  "action": "create",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1"
		|	}
		|  ]
		|}""".trimMargin()
        val instructionjsonFile = MockMultipartFile("instruction", "", "application/json", instruction.toByteArray())
        val documentjsonFile = MockMultipartFile("document", "", "application/json", document.toByteArray())
        val builder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).file(instructionjsonFile).file(documentjsonFile).with { request ->
            request.method = "PUT"
            request
        }
        mockMvc.perform(builder).andExpect(MockMvcResultMatchers.status().isOk()).andReturn()


        /* update test*/
        val kpNew1 = KeyPairGenerator().generateKeyPair()
        val kpNew2 = KeyPairGenerator().generateKeyPair()
        val pubNew1 = kpNew1.public.encoded.toBase58()
        val pubNew2 = kpNew2.public.encoded.toBase58()
        val uriNew = URI("${documentId}#keys-2")

        val documentNew = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
        |  "created": "1970-01-01T00:00:00Z",
		|  "updated": "1970-01-02T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pubNew1"
		|	},
        | {
		|	  "id": "$uriNew",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pubNew2"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1New = kpNew1.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val signature2New = kpNew2.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val encodedSignature1New = signature1New.bytes.toBase58()
        val encodedSignature2New = signature2New.bytes.toBase58()
        val instructionNew = """{
		|  "action": "update",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1New"
		|	},
		|	{
		|	  "id": "$uriNew",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature2New"
		|	}
		|  ]
		|}""".trimMargin()
        val updateBuilder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).param("instruction",instructionNew).param("document",documentNew)
        mockMvc.perform(updateBuilder).andExpect(MockMvcResultMatchers.status().is4xxClientError())



    }

    @Test
    fun `Update a DID that does not exist should fail` () {

        val uuid = UUID.randomUUID()

        val documentId="did:corda:tcn:"+uuid
        val kp = KeyPairGenerator().generateKeyPair()
        val uri = URI("${documentId}#keys-1")
        val pub = kp.public.encoded.toBase58()
        val documentNew = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
        |  "created": "1970-01-01T00:00:00Z",
		|  "updated": "1970-01-02T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1New = kp.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val encodedSignature1New = signature1New.bytes.toBase58()
        val instructionNew = """{
		|  "action": "update",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1New"
		|	}
		|  ]
		|}""".trimMargin()
        val updateBuilder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).param("instruction",instructionNew).param("document",documentNew)
        mockMvc.perform(updateBuilder).andExpect(MockMvcResultMatchers.status().isNotFound())


    }
    @Test
    fun `Update of a document with wrong uri should fail` () {
        val kp = KeyPairGenerator().generateKeyPair()

        val pub = kp.public.encoded.toBase58()

        val uuid = UUID.randomUUID()

        val documentId = "did:corda:tcn:"+uuid

        val uri = URI("${documentId}#keys-1")

        val document = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1 = kp.private.sign(document.toByteArray(Charsets.UTF_8))

        val encodedSignature1 = signature1.bytes.toBase58()

        val instruction = """{
		|  "action": "create",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1"
		|	}
		|  ]
		|}""".trimMargin()
        val instructionjsonFile = MockMultipartFile("instruction", "", "application/json", instruction.toByteArray())
        val documentjsonFile = MockMultipartFile("document", "", "application/json", document.toByteArray())
        val builder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).file(instructionjsonFile).file(documentjsonFile).with { request ->
            request.method = "PUT"
            request
        }
        mockMvc.perform(builder).andExpect(MockMvcResultMatchers.status().isOk()).andReturn()


        /* update test*/
        val kpNew = KeyPairGenerator().generateKeyPair()

        val pubNew = kpNew.public.encoded.toBase58()

        val uriNew = URI("${documentId}#keys-2")

        val documentNew = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
        |  "created": "1970-01-01T00:00:00Z",
		|  "updated": "1970-01-02T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uriNew",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	},
        | {
		|	  "id": "$uriNew",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pubNew"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1New = kp.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val signature2New = kpNew.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val encodedSignature1New = signature1New.bytes.toBase58()
        val encodedSignature2New = signature2New.bytes.toBase58()
        val instructionNew = """{
		|  "action": "update",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1New"
		|	},
		|	{
		|	  "id": "$uriNew",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature2New"
		|	}
		|  ]
		|}""".trimMargin()
        val updateBuilder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).param("instruction",instructionNew).param("document",documentNew)
        mockMvc.perform(updateBuilder).andExpect(MockMvcResultMatchers.status().is4xxClientError())



    }
    @Test
    fun `Update of a document without context should fail` () {
        val kp = KeyPairGenerator().generateKeyPair()

        val pub = kp.public.encoded.toBase58()

        val uuid = UUID.randomUUID()

        val documentId = "did:corda:tcn:"+uuid

        val uri = URI("${documentId}#keys-1")

        val document = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1 = kp.private.sign(document.toByteArray(Charsets.UTF_8))

        val encodedSignature1 = signature1.bytes.toBase58()

        val instruction = """{
		|  "action": "create",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1"
		|	}
		|  ]
		|}""".trimMargin()
        val instructionjsonFile = MockMultipartFile("instruction", "", "application/json", instruction.toByteArray())
        val documentjsonFile = MockMultipartFile("document", "", "application/json", document.toByteArray())
        val builder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).file(instructionjsonFile).file(documentjsonFile).with { request ->
            request.method = "PUT"
            request
        }
        mockMvc.perform(builder).andExpect(MockMvcResultMatchers.status().isOk()).andReturn()


        /* update test*/
        val kpNew = KeyPairGenerator().generateKeyPair()

        val pubNew = kpNew.public.encoded.toBase58()

        val uriNew = URI("${documentId}#keys-2")

        val documentNew = """{
		|  "id": "${documentId}",
        |  "created": "1970-01-01T00:00:00Z",
		|  "updated": "1970-01-02T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	},
        | {
		|	  "id": "$uriNew",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pubNew"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1New = kp.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val signature2New = kpNew.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val encodedSignature1New = signature1New.bytes.toBase58()
        val encodedSignature2New = signature2New.bytes.toBase58()
        val instructionNew = """{
		|  "action": "update",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1New"
		|	},
		|	{
		|	  "id": "$uriNew",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature2New"
		|	}
		|  ]
		|}""".trimMargin()
        val updateBuilder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).param("instruction",instructionNew).param("document",documentNew)
        mockMvc.perform(updateBuilder).andExpect(MockMvcResultMatchers.status().is4xxClientError())



    }

    @Test
    fun `Create a DID and update the document with a single public key` () {
        val kp = KeyPairGenerator().generateKeyPair()

        val pub = kp.public.encoded.toBase58()

        val uuid = UUID.randomUUID()

        val documentId = "did:corda:tcn:"+uuid

        val uri = URI("${documentId}#keys-1")

        val document = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1 = kp.private.sign(document.toByteArray(Charsets.UTF_8))

        val encodedSignature1 = signature1.bytes.toBase58()

        val instruction = """{
		|  "action": "create",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1"
		|	}
		|  ]
		|}""".trimMargin()
        val instructionjsonFile = MockMultipartFile("instruction", "", "application/json", instruction.toByteArray())
        val documentjsonFile = MockMultipartFile("document", "", "application/json", document.toByteArray())
        val builder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).file(instructionjsonFile).file(documentjsonFile).with { request ->
            request.method = "PUT"
            request
        }
        mockMvc.perform(builder).andExpect(MockMvcResultMatchers.status().isOk()).andReturn()


        /* update test*/
        val kpNew = KeyPairGenerator().generateKeyPair()

        val pubNew = kpNew.public.encoded.toBase58()

        val uriNew = URI("${documentId}#keys-2")

        val documentNew = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
        |  "created": "1970-01-01T00:00:00Z",
		|  "updated": "1970-01-02T00:00:00Z",
		|  "publicKey": [
        | {
		|	  "id": "$uriNew",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pubNew"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1New = kp.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val signature2New = kpNew.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val encodedSignature1New = signature1New.bytes.toBase58()
        val encodedSignature2New = signature2New.bytes.toBase58()
        val instructionNew = """{
		|  "action": "update",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1New"
		|	},
		|	{
		|	  "id": "$uriNew",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature2New"
		|	}
		|  ]
		|}""".trimMargin()
        val updateBuilder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).param("instruction",instructionNew).param("document",documentNew)
        mockMvc.perform(updateBuilder).andExpect(MockMvcResultMatchers.status().isOk())
        mockMvc.perform(MockMvcRequestBuilders.get(apiUrl+"did:corda:tcn:"+uuid.toString())).andExpect(MockMvcResultMatchers.status().isOk()).andExpect(MockMvcResultMatchers.content().json(documentNew)).andReturn()



    }
    @Test
    fun `Update the document with mismatching DID should fail` () {
        val kp = KeyPairGenerator().generateKeyPair()

        val pub = kp.public.encoded.toBase58()

        val uuid = UUID.randomUUID()

        val documentId = "did:corda:tcn:"+uuid

        val uri = URI("${documentId}#keys-1")

        val document = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
		|  "created": "1970-01-01T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1 = kp.private.sign(document.toByteArray(Charsets.UTF_8))

        val encodedSignature1 = signature1.bytes.toBase58()

        val instruction = """{
		|  "action": "create",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1"
		|	}
		|  ]
		|}""".trimMargin()
        val instructionjsonFile = MockMultipartFile("instruction", "", "application/json", instruction.toByteArray())
        val documentjsonFile = MockMultipartFile("document", "", "application/json", document.toByteArray())
        val builder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+uuid.toString()).file(instructionjsonFile).file(documentjsonFile).with { request ->
            request.method = "PUT"
            request
        }
        mockMvc.perform(builder).andExpect(MockMvcResultMatchers.status().isOk()).andReturn()


        /* update test*/
        val kpNew = KeyPairGenerator().generateKeyPair()

        val pubNew = kpNew.public.encoded.toBase58()

        val uriNew = URI("${documentId}#keys-2")

        val documentNew = """{
		|  "@context": "https://w3id.org/did/v1",
		|  "id": "${documentId}",
        |  "created": "1970-01-01T00:00:00Z",
		|  "updated": "1970-01-02T00:00:00Z",
		|  "publicKey": [
		|	{
		|	  "id": "$uri",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pub"
		|	},
        | {
		|	  "id": "$uriNew",
		|	  "type": "${CryptoSuite.Ed25519.keyID}",
		|	  "controller": "${documentId}",
		|	  "publicKeyBase58": "$pubNew"
		|	}
		|  ]
		|}""".trimMargin()

        val signature1New = kp.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val signature2New = kpNew.private.sign(documentNew.toByteArray(Charsets.UTF_8))
        val encodedSignature1New = signature1New.bytes.toBase58()
        val encodedSignature2New = signature2New.bytes.toBase58()
        val instructionNew = """{
		|  "action": "update",
		|  "signatures": [
		|	{
		|	  "id": "$uri",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature1New"
		|	},
		|	{
		|	  "id": "$uriNew",
		|	  "type": "Ed25519Signature2018",
		|	  "signatureBase58": "$encodedSignature2New"
		|	}
		|  ]
		|}""".trimMargin()
        val updateBuilder = MockMvcRequestBuilders.fileUpload(apiUrl+"did:corda:tcn:"+UUID.randomUUID().toString()).param("instruction",instructionNew).param("document",documentNew)
        mockMvc.perform(updateBuilder).andExpect(MockMvcResultMatchers.status().is4xxClientError())



    }


}