did:corda
=========
see readme_orig.md for details.

Prerequisites
=============
java 1.8

Run Setup
=========
3 witness nodes PartyA, PartyB and PartyC, and one notary.
```
./gradlew clean build deployNodes copyCordappConf -x test
and then in one terminal each,
./starta.sh
./startb.sh
./startc.sh
./startnotary.sh
./gradlew runPartyAServer
```
