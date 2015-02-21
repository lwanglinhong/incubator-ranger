/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

 package com.xasecure.entity;

/**
 * Audit Log for Policy Export
 *
 */

import java.util.*;
import javax.persistence.*;
import javax.xml.bind.annotation.*;
import com.xasecure.common.*;
import com.xasecure.entity.*;


@Entity
@Table(name="x_policy_export_audit")
@XmlRootElement
public class XXPolicyExportAudit extends XXDBBase implements java.io.Serializable {
	private static final long serialVersionUID = 1L;


	@Id
	@SequenceGenerator(name="X_POLICY_EXPORT_SEQ",sequenceName="X_POLICY_EXPORT_SEQ",allocationSize=1)
	@GeneratedValue(strategy=GenerationType.AUTO,generator="X_POLICY_EXPORT_SEQ")
	@Column(name="ID")
	protected Long id;
	@Override
	public void setId(Long id) {
		this.id=id;
	}

	@Override
	public Long getId() {
		return id;
	}

	/**
	 * XA Agent IP Address
	 * <ul>
	 * <li>The maximum length for this attribute is <b>255</b>.
	 * </ul>
	 *
	 */
	@Column(name="CLIENT_IP"  , nullable=false , length=255)
	protected String clientIP;

	/**
	 * XA Agent Id
	 * <ul>
	 * <li>The maximum length for this attribute is <b>255</b>.
	 * </ul>
	 *
	 */
	@Column(name="AGENT_ID"   , length=255)
	protected String agentId;

	/**
	 * Last update timestamp in request
	 * <ul>
	 * </ul>
	 *
	 */
	@Column(name="REQ_EPOCH"  , nullable=false )
	protected Long requestedEpoch;

	/**
	 * Date and time of the last policy update
	 * <ul>
	 * </ul>
	 *
	 */
	@Temporal(TemporalType.TIMESTAMP)
	@Column(name="LAST_UPDATED"   )
	protected Date lastUpdated;

	/**
	 * Name of the Asset
	 * <ul>
	 * <li>The maximum length for this attribute is <b>1024</b>.
	 * </ul>
	 *
	 */
	@Column(name="REPOSITORY_NAME"   , length=1024)
	protected String repositoryName;

	/**
	 * JSON of the policies exported
	 * <ul>
	 * <li>The maximum length for this attribute is <b>30000</b>.
	 * </ul>
	 *
	 */
	@Column(name="EXPORTED_JSON"   , length=30000)
	protected String exportedJson;

	/**
	 * HTTP Response Code
	 * <ul>
	 * </ul>
	 *
	 */
	@Column(name="HTTP_RET_CODE"  , nullable=false )
	protected int httpRetCode;

	/**
	 * Default constructor. This will set all the attributes to default value.
	 */
	public XXPolicyExportAudit ( ) {
	}

	@Override
	public int getMyClassType( ) {
	    return AppConstants.CLASS_TYPE_XA_POLICY_EXPORT_AUDIT;
	}

	/**
	 * This method sets the value to the member attribute <b>clientIP</b>.
	 * You cannot set null to the attribute.
	 * @param clientIP Value to set member attribute <b>clientIP</b>
	 */
	public void setClientIP( String clientIP ) {
		this.clientIP = clientIP;
	}

	/**
	 * Returns the value for the member attribute <b>clientIP</b>
	 * @return String - value of member attribute <b>clientIP</b>.
	 */
	public String getClientIP( ) {
		return this.clientIP;
	}

	/**
	 * This method sets the value to the member attribute <b>agentId</b>.
	 * You cannot set null to the attribute.
	 * @param agentId Value to set member attribute <b>agentId</b>
	 */
	public void setAgentId( String agentId ) {
		this.agentId = agentId;
	}

	/**
	 * Returns the value for the member attribute <b>agentId</b>
	 * @return String - value of member attribute <b>agentId</b>.
	 */
	public String getAgentId( ) {
		return this.agentId;
	}

	/**
	 * This method sets the value to the member attribute <b>requestedEpoch</b>.
	 * You cannot set null to the attribute.
	 * @param requestedEpoch Value to set member attribute <b>requestedEpoch</b>
	 */
	public void setRequestedEpoch( Long requestedEpoch ) {
		this.requestedEpoch = requestedEpoch;
	}

	/**
	 * Returns the value for the member attribute <b>requestedEpoch</b>
	 * @return Long - value of member attribute <b>requestedEpoch</b>.
	 */
	public Long getRequestedEpoch( ) {
		return this.requestedEpoch;
	}

	/**
	 * This method sets the value to the member attribute <b>lastUpdated</b>.
	 * You cannot set null to the attribute.
	 * @param lastUpdated Value to set member attribute <b>lastUpdated</b>
	 */
	public void setLastUpdated( Date lastUpdated ) {
		this.lastUpdated = lastUpdated;
	}

	/**
	 * Returns the value for the member attribute <b>lastUpdated</b>
	 * @return Date - value of member attribute <b>lastUpdated</b>.
	 */
	public Date getLastUpdated( ) {
		return this.lastUpdated;
	}

	/**
	 * This method sets the value to the member attribute <b>repositoryName</b>.
	 * You cannot set null to the attribute.
	 * @param repositoryName Value to set member attribute <b>repositoryName</b>
	 */
	public void setRepositoryName( String repositoryName ) {
		this.repositoryName = repositoryName;
	}

	/**
	 * Returns the value for the member attribute <b>repositoryName</b>
	 * @return String - value of member attribute <b>repositoryName</b>.
	 */
	public String getRepositoryName( ) {
		return this.repositoryName;
	}

	/**
	 * This method sets the value to the member attribute <b>exportedJson</b>.
	 * You cannot set null to the attribute.
	 * @param exportedJson Value to set member attribute <b>exportedJson</b>
	 */
	public void setExportedJson( String exportedJson ) {
		this.exportedJson = exportedJson;
	}

	/**
	 * Returns the value for the member attribute <b>exportedJson</b>
	 * @return String - value of member attribute <b>exportedJson</b>.
	 */
	public String getExportedJson( ) {
		return this.exportedJson;
	}

	/**
	 * This method sets the value to the member attribute <b>httpRetCode</b>.
	 * You cannot set null to the attribute.
	 * @param httpRetCode Value to set member attribute <b>httpRetCode</b>
	 */
	public void setHttpRetCode( int httpRetCode ) {
		this.httpRetCode = httpRetCode;
	}

	/**
	 * Returns the value for the member attribute <b>httpRetCode</b>
	 * @return int - value of member attribute <b>httpRetCode</b>.
	 */
	public int getHttpRetCode( ) {
		return this.httpRetCode;
	}

	/**
	 * This return the bean content in string format
	 * @return formatedStr
	*/
	@Override
	public String toString( ) {
		String str = "XXPolicyExportAudit={";
		str += super.toString();
		str += "clientIP={" + clientIP + "} ";
		str += "agentId={" + agentId + "} ";
		str += "requestedEpoch={" + requestedEpoch + "} ";
		str += "lastUpdated={" + lastUpdated + "} ";
		str += "repositoryName={" + repositoryName + "} ";
		str += "exportedJson={" + exportedJson + "} ";
		str += "httpRetCode={" + httpRetCode + "} ";
		str += "}";
		return str;
	}

	/**
	 * Checks for all attributes except referenced db objects
	 * @return true if all attributes match
	*/
	@Override
	public boolean equals( Object obj) {
		if ( !super.equals(obj) ) {
			return false;
		}
		XXPolicyExportAudit other = (XXPolicyExportAudit) obj;
        	if ((this.clientIP == null && other.clientIP != null) || (this.clientIP != null && !this.clientIP.equals(other.clientIP))) {
            		return false;
        	}
        	if ((this.agentId == null && other.agentId != null) || (this.agentId != null && !this.agentId.equals(other.agentId))) {
            		return false;
        	}
        	if ((this.requestedEpoch == null && other.requestedEpoch != null) || (this.requestedEpoch != null && !this.requestedEpoch.equals(other.requestedEpoch))) {
            		return false;
        	}
        	if ((this.lastUpdated == null && other.lastUpdated != null) || (this.lastUpdated != null && !this.lastUpdated.equals(other.lastUpdated))) {
            		return false;
        	}
        	if ((this.repositoryName == null && other.repositoryName != null) || (this.repositoryName != null && !this.repositoryName.equals(other.repositoryName))) {
            		return false;
        	}
        	if ((this.exportedJson == null && other.exportedJson != null) || (this.exportedJson != null && !this.exportedJson.equals(other.exportedJson))) {
            		return false;
        	}
		if( this.httpRetCode != other.httpRetCode ) return false;
		return true;
	}
	public static String getEnumName(String fieldName ) {
		//Later TODO
		//return super.getEnumName(fieldName);
		return null;
	}

}