# fabric-chaincode-scala

[ ![Download](https://api.bintray.com/packages/enterprisedlt/fabric/fabric-chaincode-scala/images/download.svg) ](https://bintray.com/enterprisedlt/fabric/fabric-chaincode-scala/_latestVersion)
 
Scala library to write smart contracts on [Hyperledger Fabric](https://www.hyperledger.org/projects/fabric) blockchain platform.
This library wraps [Fabric chain code java](https://github.com/hyperledger/fabric-chaincode-java) and adds functionality to make chain code looks more clear.

Features:
 - Object oriented design of Shim API
 - Bind of methods thru annotations.
 - Automatic check of arguments count and types
 - Automatic deserialization of arguments (from JSON by default)
 - Automatic result serialization (to JSON by default)
 - Customizable serialization/deserialization of arguments and Ledger entries.

For usage examples check out [Fabric contract examples](https://github.com/apolubelov/fabric-contract-examples) repo.