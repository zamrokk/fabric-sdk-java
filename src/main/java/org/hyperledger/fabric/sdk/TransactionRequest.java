/*
 *  Copyright 2016 DTCC, Fujitsu Australia Software Technology - All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 	  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.hyperledger.fabric.sdk;

import java.util.ArrayList;

/**
 * A base transaction request common for DeployRequest, InvokeRequest, and QueryRequest.
 */
public class TransactionRequest {
    // The local path containing the chaincode to deploy in network mode.
    private String chaincodePath;
    // The name identifier for the chaincode to deploy in development mode.
    private String chaincodeName;
	// The chaincode ID as provided by the 'submitted' event emitted by a TransactionContext
    private String chaincodeID;
    // The name of the function to invoke
    private String fcn;
    // The arguments to pass to the chaincode invocation
    private ArrayList<String> args;
    // Specify whether the transaction is confidential or not.  The default value is false.
    private boolean confidential = false;
    // Optionally provide a user certificate which can be used by chaincode to perform access control
    private Certificate userCert;
    // Optionally provide additional metadata
    private byte[] metadata;
    // Chaincode language
    private ChaincodeLanguage chaincodeLanguage = ChaincodeLanguage.GO_LANG;

	public String getChaincodePath() {
		return null == chaincodePath ? "" : chaincodePath;
	}
	public void setChaincodePath(String chaincodePath) {
		this.chaincodePath = chaincodePath;
	}
	public String getChaincodeName() {
		return chaincodeName;
	}
	public void setChaincodeName(String chaincodeName) {
		this.chaincodeName = chaincodeName;
	}
	public String getChaincodeID() {
		return chaincodeID;
	}
	public void setChaincodeID(String chaincodeID) {
		this.chaincodeID = chaincodeID;
	}
	public String getFcn() {
		return fcn;
	}
	public void setFcn(String fcn) {
		this.fcn = fcn;
	}
	public ArrayList<String> getArgs() {
		return args;
	}
	public void setArgs(ArrayList<String> args) {
		this.args = args;
	}
	public boolean isConfidential() {
		return confidential;
	}
	public void setConfidential(boolean confidential) {
		this.confidential = confidential;
	}
	public Certificate getUserCert() {
		return userCert;
	}
	public void setUserCert(Certificate userCert) {
		this.userCert = userCert;
	}
	public byte[] getMetadata() {
		return metadata;
	}
	public void setMetadata(byte[] metadata) {
		this.metadata = metadata;
	}
	public ChaincodeLanguage getChaincodeLanguage() {
		return chaincodeLanguage;
	}
	public void setChaincodeLanguage(ChaincodeLanguage chaincodeLanguage) {
		this.chaincodeLanguage = chaincodeLanguage;
	}
}
