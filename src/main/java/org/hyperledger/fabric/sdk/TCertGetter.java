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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperledger.fabric.sdk.exception.GetTCertBatchException;

import java.util.List;
import java.util.Stack;

// A class to get TCerts.
// There is one class per set of attributes requested by each member.
public class TCertGetter {

    private static final Log logger = LogFactory.getLog(TCertGetter.class);

    private Chain chain;
    private Member member;
    private List<String> attrs;
    private String key;
    private MemberServices memberServices;
    private Stack<TCert> tcerts;
//TODO implement stats
//    private stats.Rate arrivalRate = new stats.Rate();
//    private stats.ResponseTime getTCertResponseTime = new stats.ResponseTime();
//    private getTCertWaiters:GetTCertCallback[] = [];
    private boolean gettingTCerts = false;

    /**
    * Constructor for a member.
    * @param member member
    * @param attrs 
    * @param key
    */
    public TCertGetter(Member member, List<String> attrs, String key) {
        this.member = member;
        this.attrs = attrs;
        this.key = key;
        this.chain = member.getChain();
        this.memberServices = member.getMemberServices();
        this.tcerts = new Stack<>();
    }

    /**
    * Get the chain.
    * @return {Chain} The chain.
    */
    public Chain getChain() {
        return this.chain;
    };

    public void getUserCert() {
        this.getNextTCert();
    }

    /**
    * Get the next available transaction certificate.
    */
    public TCert getNextTCert() {

//TODO    	self.arrivalRate.tick();

        if (shouldGetTCerts()) {
            getTCerts();
        }

        if (tcerts.size() > 0) {
            return tcerts.pop();
        } else {
            return null;
        }
    }

    // Determine if we should issue a request to get more tcerts now.
    private boolean shouldGetTCerts() {
        return tcerts.size() == 0;        //TODO implement shouldGetTCerts


    	/*
    	let self = this;
        // Do nothing if we are already getting more tcerts
        if (self.gettingTCerts) {
            debug("shouldGetTCerts: no, already getting tcerts");
            return false;
        }
        // If there are none, then definitely get more
        if (self.tcerts.length == 0) {
            debug("shouldGetTCerts: yes, we have no tcerts");
            return true;
        }
        // If we aren't in prefetch mode, return false;
        if (!self.chain.isPreFetchMode()) {
            debug("shouldGetTCerts: no, prefetch disabled");
            return false;
        }
        // Otherwise, see if we should prefetch based on the arrival rate
        // (i.e. the rate at which tcerts are requested) and the response
        // time.
        // "arrivalRate" is in req/ms and "responseTime" in ms,
        // so "tcertCountThreshold" is number of tcerts at which we should
        // request the next batch of tcerts so we don't have to wait on the
        // transaction path.  Note that we add 1 sec to the average response
        // time to add a little buffer time so we don't have to wait.
        let arrivalRate = self.arrivalRate.getValue();
        let responseTime = self.getTCertResponseTime.getValue() + 1000;
        let tcertThreshold = arrivalRate * responseTime;
        let tcertCount = self.tcerts.length;
        let result = tcertCount <= tcertThreshold;
        debug(util.format("shouldGetTCerts: %s, threshold=%s, count=%s, rate=%s, responseTime=%s",
        result, tcertThreshold, tcertCount, arrivalRate, responseTime));
        return result;

        */
    }

    // Call member services to get more tcerts
    private void getTCerts() {
        GetTCertBatchRequest req = new GetTCertBatchRequest(this.member.getName(), this.member.getEnrollment(),
                this.member.getTCertBatchSize(), attrs);
        try {
            List<TCert> tcerts = this.memberServices.getTCertBatch(req);
            // Add to member's tcert list
            for (TCert tcert : tcerts) {
                this.tcerts.push(tcert);
            }
        } catch (GetTCertBatchException e) {
            // ignore the exception for now
        }
    }
} // end TCertGetter
