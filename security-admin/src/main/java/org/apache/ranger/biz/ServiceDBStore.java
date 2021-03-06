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

package org.apache.ranger.biz;

import java.util.*;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ranger.authorization.hadoop.config.RangerConfiguration;
import org.apache.ranger.common.*;
import org.apache.ranger.db.*;
import org.apache.ranger.entity.*;
import org.apache.ranger.plugin.model.RangerPolicy;
import org.apache.ranger.plugin.model.RangerPolicy.RangerDataMaskPolicyItem;
import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyItem;
import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyItemAccess;
import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyItemCondition;
import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyResource;
import org.apache.ranger.plugin.model.RangerPolicyResourceSignature;
import org.apache.ranger.plugin.model.RangerService;
import org.apache.ranger.plugin.model.RangerServiceDef;
import org.apache.ranger.plugin.model.RangerServiceDef.RangerAccessTypeDef;
import org.apache.ranger.plugin.model.RangerServiceDef.RangerContextEnricherDef;
import org.apache.ranger.plugin.model.RangerServiceDef.RangerDataMaskDef;
import org.apache.ranger.plugin.model.RangerServiceDef.RangerDataMaskTypeDef;
import org.apache.ranger.plugin.model.RangerServiceDef.RangerEnumDef;
import org.apache.ranger.plugin.model.RangerServiceDef.RangerEnumElementDef;
import org.apache.ranger.plugin.model.RangerServiceDef.RangerPolicyConditionDef;
import org.apache.ranger.plugin.model.RangerServiceDef.RangerResourceDef;
import org.apache.ranger.plugin.model.RangerServiceDef.RangerServiceConfigDef;
import org.apache.ranger.plugin.model.validation.RangerServiceDefHelper;
import org.apache.ranger.plugin.policyevaluator.RangerPolicyItemEvaluator;
import org.apache.ranger.plugin.store.*;
import org.apache.ranger.plugin.util.SearchFilter;
import org.apache.ranger.plugin.util.ServicePolicies;
import org.apache.ranger.service.RangerAuditFields;
import org.apache.ranger.service.RangerDataHistService;
import org.apache.ranger.service.RangerPolicyService;
import org.apache.ranger.service.RangerPolicyWithAssignedIdService;
import org.apache.ranger.service.RangerServiceDefService;
import org.apache.ranger.service.RangerServiceDefWithAssignedIdService;
import org.apache.ranger.service.RangerServiceService;
import org.apache.ranger.service.RangerServiceWithAssignedIdService;
import org.apache.ranger.service.XUserService;
import org.apache.ranger.view.RangerPolicyList;
import org.apache.ranger.view.RangerServiceDefList;
import org.apache.ranger.view.RangerServiceList;
import org.apache.ranger.view.VXString;
import org.apache.ranger.view.VXUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;


@Component
public class ServiceDBStore extends AbstractServiceStore {
	private static final Log LOG = LogFactory.getLog(ServiceDBStore.class);
	public static final String RANGER_TAG_EXPIRY_CONDITION_NAME = "accessed-after-expiry";

	@Autowired
	RangerServiceDefService serviceDefService;
 
	@Autowired
	RangerDaoManager daoMgr;

	@Autowired
	RESTErrorUtil restErrorUtil;
	
	@Autowired
	RangerServiceService svcService;
	
	@Autowired
	StringUtil stringUtil;
	
	@Autowired
	RangerAuditFields<XXDBBase> rangerAuditFields;
	
	@Autowired
	RangerPolicyService policyService;
	
	@Autowired
	XUserService xUserService;
	
	@Autowired
	XUserMgr xUserMgr;
	
	@Autowired
	RangerDataHistService dataHistService;

    @Autowired
    @Qualifier(value = "transactionManager")
    PlatformTransactionManager txManager;
    
    @Autowired
    RangerBizUtil bizUtil;
    
    @Autowired
    RangerPolicyWithAssignedIdService assignedIdPolicyService;
    
    @Autowired
    RangerServiceWithAssignedIdService svcServiceWithAssignedId;
    
    @Autowired
    RangerServiceDefWithAssignedIdService svcDefServiceWithAssignedId;

    @Autowired
    RangerFactory factory;

    
	private static volatile boolean legacyServiceDefsInitDone = false;
	private Boolean populateExistingBaseFields = false;
	
	public static final String HIDDEN_PASSWORD_STR = "*****";
	public static final String CONFIG_KEY_PASSWORD = "password";

	private ServicePredicateUtil predicateUtil = null;


	@Override
	public void init() throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDefDBStore.init()");
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDefDBStore.init()");
		}
	}

	@PostConstruct
	public void initStore() {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDefDBStore.initStore()");
		}

		if(! legacyServiceDefsInitDone) {
			synchronized(ServiceDBStore.class) {
				if(!legacyServiceDefsInitDone) {

					if (! RangerConfiguration.getInstance().addAdminResources()) {
						LOG.error("Could not add ranger-admin resources to RangerConfiguration.");
					}

					TransactionTemplate txTemplate = new TransactionTemplate(txManager);

					final ServiceDBStore dbStore = this;
					predicateUtil = new ServicePredicateUtil(dbStore);


					txTemplate.execute(new TransactionCallback<Object>() {
						@Override
	                    public Object doInTransaction(TransactionStatus status) {
							EmbeddedServiceDefsUtil.instance().init(dbStore);

							return null;
	                    }
					});

					legacyServiceDefsInitDone = true;
				}
			}
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDefDBStore.initStore()");
		}
	}

	@Override
	public RangerServiceDef createServiceDef(RangerServiceDef serviceDef) throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDefDBStore.createServiceDef(" + serviceDef + ")");
		}

		XXServiceDef xServiceDef = daoMgr.getXXServiceDef().findByName(
				serviceDef.getName());
		if (xServiceDef != null) {
			throw restErrorUtil.createRESTException("service-def with name: "
					+ serviceDef.getName() + " already exists",
					MessageEnums.ERROR_DUPLICATE_OBJECT);
		}
		
		List<RangerServiceConfigDef> configs = serviceDef.getConfigs();
		List<RangerResourceDef> resources = serviceDef.getResources();
		List<RangerAccessTypeDef> accessTypes = serviceDef.getAccessTypes();
		List<RangerPolicyConditionDef> policyConditions = serviceDef.getPolicyConditions();
		List<RangerContextEnricherDef> contextEnrichers = serviceDef.getContextEnrichers();
		List<RangerEnumDef> enums = serviceDef.getEnums();
		RangerDataMaskDef dataMaskDef = serviceDef.getDataMaskDef();

		
		// While creating, value of version should be 1.
		serviceDef.setVersion(Long.valueOf(1));
		
		if (populateExistingBaseFields) {
			svcDefServiceWithAssignedId.setPopulateExistingBaseFields(true);
			daoMgr.getXXServiceDef().setIdentityInsert(true);

			svcDefServiceWithAssignedId.create(serviceDef);

			svcDefServiceWithAssignedId.setPopulateExistingBaseFields(false);
			daoMgr.getXXServiceDef().updateSequence();
			daoMgr.getXXServiceDef().setIdentityInsert(false);
		} else {
			// following fields will be auto populated
			serviceDef.setId(null);
			serviceDef.setCreateTime(null);
			serviceDef.setUpdateTime(null);
			serviceDef = serviceDefService.create(serviceDef);
		}
		Long serviceDefId = serviceDef.getId();
		XXServiceDef createdSvcDef = daoMgr.getXXServiceDef().getById(serviceDefId);
		
		XXServiceConfigDefDao xxServiceConfigDao = daoMgr.getXXServiceConfigDef();
		for(int i = 0; i < configs.size(); i++) {
			RangerServiceConfigDef config = configs.get(i);

			XXServiceConfigDef xConfig = new XXServiceConfigDef();
			xConfig = serviceDefService.populateRangerServiceConfigDefToXX(config, xConfig, createdSvcDef,
					RangerServiceDefService.OPERATION_CREATE_CONTEXT);
			xConfig.setOrder(i);
			xConfig = xxServiceConfigDao.create(xConfig);
		}
		
		XXResourceDefDao xxResDefDao = daoMgr.getXXResourceDef();
		for(int i = 0; i < resources.size(); i++) {
			RangerResourceDef resource = resources.get(i);

			XXResourceDef parent = xxResDefDao.findByNameAndServiceDefId(resource.getParent(), serviceDefId);
			Long parentId = (parent != null) ? parent.getId() : null;
			
			XXResourceDef xResource = new XXResourceDef();
			xResource = serviceDefService.populateRangerResourceDefToXX(resource, xResource, createdSvcDef,
					RangerServiceDefService.OPERATION_CREATE_CONTEXT);
			xResource.setOrder(i);
			xResource.setParent(parentId);
			xResource = xxResDefDao.create(xResource);
		}
		
		XXAccessTypeDefDao xxATDDao = daoMgr.getXXAccessTypeDef();
		for(int i = 0; i < accessTypes.size(); i++) {
			RangerAccessTypeDef accessType = accessTypes.get(i);

			XXAccessTypeDef xAccessType = new XXAccessTypeDef();
			xAccessType = serviceDefService.populateRangerAccessTypeDefToXX(accessType, xAccessType, createdSvcDef,
					RangerServiceDefService.OPERATION_CREATE_CONTEXT);
			xAccessType.setOrder(i);
			xAccessType = xxATDDao.create(xAccessType);
			
			Collection<String> impliedGrants = accessType.getImpliedGrants();
			XXAccessTypeDefGrantsDao xxATDGrantDao = daoMgr.getXXAccessTypeDefGrants();
			for(String impliedGrant : impliedGrants) {
				XXAccessTypeDefGrants xImpliedGrant = new XXAccessTypeDefGrants();
				xImpliedGrant.setAtdId(xAccessType.getId());
				xImpliedGrant.setImpliedGrant(impliedGrant);
				xImpliedGrant = xxATDGrantDao.create(xImpliedGrant);
			}
		}
		
		XXPolicyConditionDefDao xxPolCondDao = daoMgr.getXXPolicyConditionDef();
		for (int i = 0; i < policyConditions.size(); i++) {
			RangerPolicyConditionDef policyCondition = policyConditions.get(i);

			XXPolicyConditionDef xPolicyCondition = new XXPolicyConditionDef();
			xPolicyCondition = serviceDefService
					.populateRangerPolicyConditionDefToXX(policyCondition,
							xPolicyCondition, createdSvcDef, RangerServiceDefService.OPERATION_CREATE_CONTEXT);
			xPolicyCondition.setOrder(i);
			xPolicyCondition = xxPolCondDao.create(xPolicyCondition);
		}
		
		XXContextEnricherDefDao xxContextEnricherDao = daoMgr.getXXContextEnricherDef();
		for (int i = 0; i < contextEnrichers.size(); i++) {
			RangerContextEnricherDef contextEnricher = contextEnrichers.get(i);

			XXContextEnricherDef xContextEnricher = new XXContextEnricherDef();
			xContextEnricher = serviceDefService
					.populateRangerContextEnricherDefToXX(contextEnricher,
							xContextEnricher, createdSvcDef, RangerServiceDefService.OPERATION_CREATE_CONTEXT);
			xContextEnricher.setOrder(i);
			xContextEnricher = xxContextEnricherDao.create(xContextEnricher);
		}
		
		XXEnumDefDao xxEnumDefDao = daoMgr.getXXEnumDef();
		for(RangerEnumDef vEnum : enums) {
			XXEnumDef xEnum = new XXEnumDef();
			xEnum = serviceDefService.populateRangerEnumDefToXX(vEnum, xEnum, createdSvcDef, RangerServiceDefService.OPERATION_CREATE_CONTEXT);
			xEnum = xxEnumDefDao.create(xEnum);
			
			List<RangerEnumElementDef> elements = vEnum.getElements();
			XXEnumElementDefDao xxEnumEleDefDao = daoMgr.getXXEnumElementDef();
			for(int i = 0; i < elements.size(); i++) {
				RangerEnumElementDef element = elements.get(i);

				XXEnumElementDef xElement = new XXEnumElementDef();
				xElement = serviceDefService.populateRangerEnumElementDefToXX(element, xElement, xEnum, RangerServiceDefService.OPERATION_CREATE_CONTEXT);
				xElement.setOrder(i);
				xElement = xxEnumEleDefDao.create(xElement);
			}
		}

		if(dataMaskDef != null) {
			List<RangerDataMaskTypeDef> dataMaskTypes        = dataMaskDef.getMaskTypes();
			List<String>                supportedAccessTypes = dataMaskDef.getSupportedAccessTypes();
			List<String>                supportedResources   = dataMaskDef.getSupportedResources();

			if(CollectionUtils.isNotEmpty(dataMaskTypes)) {
				XXDataMaskTypeDefDao xxDataMaskDefDao = daoMgr.getXXDataMaskTypeDef();
				for (int i = 0; i < dataMaskTypes.size(); i++) {
					RangerDataMaskTypeDef dataMask = dataMaskTypes.get(i);

					XXDataMaskTypeDef xDataMaskDef = new XXDataMaskTypeDef();
					xDataMaskDef = serviceDefService.populateRangerDataMaskDefToXX(dataMask, xDataMaskDef, createdSvcDef,
							RangerServiceDefService.OPERATION_CREATE_CONTEXT);
					xDataMaskDef.setOrder(i);
					xDataMaskDef = xxDataMaskDefDao.create(xDataMaskDef);
				}
			}

			if(CollectionUtils.isNotEmpty(supportedAccessTypes)) {
				List<XXAccessTypeDef> xxAccessTypeDefs = xxATDDao.findByServiceDefId(xServiceDef.getId());

				for(String accessType : supportedAccessTypes) {
					boolean found = false;
					for(XXAccessTypeDef xxAccessTypeDef : xxAccessTypeDefs) {
						if(StringUtils.equals(xxAccessTypeDef.getName(), accessType)) {
							found = true;
							break;
						}
					}

					if(! found) {
						throw restErrorUtil.createRESTException("accessType with name: "
										+ accessType + " does not exists", MessageEnums.DATA_NOT_FOUND);
					}
				}

				for(XXAccessTypeDef xxAccessTypeDef : xxAccessTypeDefs) {
					boolean isDatamaskingSupported = supportedAccessTypes.contains(xxAccessTypeDef.getName());

					if(xxAccessTypeDef.isDatamaskingSupported() != isDatamaskingSupported) {
						xxAccessTypeDef.setDatamaskingSupported(isDatamaskingSupported);
						xxATDDao.update(xxAccessTypeDef);
					}
				}
			}

			if(CollectionUtils.isNotEmpty(supportedResources)) {
				List<XXResourceDef> xxResourceDefs = xxResDefDao.findByServiceDefId(xServiceDef.getId());

				for(String resource : supportedResources) {
					boolean found = false;
					for(XXResourceDef xxResourceDef : xxResourceDefs) {
						if(StringUtils.equals(xxResourceDef.getName(), resource)) {
							found = true;
							break;
						}
					}

					if(! found) {
						throw restErrorUtil.createRESTException("resource with name: "
								+ resource + " does not exists", MessageEnums.DATA_NOT_FOUND);
					}
				}

				for(XXResourceDef xxResourceDef : xxResourceDefs) {
					boolean isDatamaskingSupported = supportedResources.contains(xxResourceDef.getName());

					if(xxResourceDef.isDatamaskingSupported() != isDatamaskingSupported) {
						xxResourceDef.setDatamaskingSupported(isDatamaskingSupported);
						xxResDefDao.update(xxResourceDef);
					}
				}
			}
		}

		RangerServiceDef createdServiceDef = serviceDefService.getPopulatedViewObject(createdSvcDef);
		dataHistService.createObjectDataHistory(createdServiceDef, RangerDataHistService.ACTION_CREATE);

		postCreate(createdServiceDef);

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDefDBStore.createServiceDef(" + serviceDef + "): " + createdServiceDef);
		}

		return createdServiceDef;
	}

	@Override
	public RangerServiceDef updateServiceDef(RangerServiceDef serviceDef) throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDefDBStore.updateServiceDef(" + serviceDef + ")");
		}

		Long serviceDefId = serviceDef.getId();

		XXServiceDef existing = daoMgr.getXXServiceDef().getById(serviceDefId);
		if (existing == null) {
			throw restErrorUtil.createRESTException("no service-def exists with ID=" + serviceDef.getId(),
					MessageEnums.DATA_NOT_FOUND);
		}

		String existingName = existing.getName();

		boolean renamed = !StringUtils.equalsIgnoreCase(serviceDef.getName(), existingName);

		if (renamed) {
			XXServiceDef renamedSVCDef = daoMgr.getXXServiceDef().findByName(serviceDef.getName());

			if (renamedSVCDef != null) {
				throw restErrorUtil.createRESTException(
						"another service-def already exists with name '" + serviceDef.getName() + "'. ID="
								+ renamedSVCDef.getId(), MessageEnums.DATA_NOT_UPDATABLE);
			}
		}

		List<RangerServiceConfigDef> configs 			= serviceDef.getConfigs() != null 			? serviceDef.getConfigs()   		  : new ArrayList<RangerServiceConfigDef>();
		List<RangerResourceDef> resources 				= serviceDef.getResources() != null  		? serviceDef.getResources() 		  : new ArrayList<RangerResourceDef>();
		List<RangerAccessTypeDef> accessTypes 			= serviceDef.getAccessTypes() != null 		? serviceDef.getAccessTypes() 	  	  : new ArrayList<RangerAccessTypeDef>();
		List<RangerPolicyConditionDef> policyConditions = serviceDef.getPolicyConditions() != null 	? serviceDef.getPolicyConditions() 	  : new ArrayList<RangerPolicyConditionDef>();
		List<RangerContextEnricherDef> contextEnrichers = serviceDef.getContextEnrichers() != null 	? serviceDef.getContextEnrichers() 	  : new ArrayList<RangerContextEnricherDef>();
		List<RangerEnumDef> enums 						= serviceDef.getEnums() != null 			? serviceDef.getEnums() 			  : new ArrayList<RangerEnumDef>();
		RangerDataMaskDef dataMaskDef                   = serviceDef.getDataMaskDef();

		serviceDef.setCreateTime(existing.getCreateTime());
		serviceDef.setGuid(existing.getGuid());
		serviceDef.setVersion(existing.getVersion());

		serviceDef = serviceDefService.update(serviceDef);
		XXServiceDef createdSvcDef = daoMgr.getXXServiceDef().getById(serviceDefId);

		updateChildObjectsOfServiceDef(createdSvcDef, configs, resources, accessTypes, policyConditions, contextEnrichers, enums, dataMaskDef);

		RangerServiceDef updatedSvcDef = getServiceDef(serviceDefId);
		dataHistService.createObjectDataHistory(updatedSvcDef, RangerDataHistService.ACTION_UPDATE);

		postUpdate(updatedSvcDef);


		if (LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDefDBStore.updateServiceDef(" + serviceDef + "): " + serviceDef);
		}

		return updatedSvcDef;
	}

	private void updateChildObjectsOfServiceDef(XXServiceDef createdSvcDef, List<RangerServiceConfigDef> configs,
			List<RangerResourceDef> resources, List<RangerAccessTypeDef> accessTypes,
			List<RangerPolicyConditionDef> policyConditions, List<RangerContextEnricherDef> contextEnrichers,
			List<RangerEnumDef> enums, RangerServiceDef.RangerDataMaskDef dataMaskDef) {

		Long serviceDefId = createdSvcDef.getId();

		List<XXServiceConfigDef> xxConfigs = daoMgr.getXXServiceConfigDef().findByServiceDefId(serviceDefId);
		List<XXResourceDef> xxResources = daoMgr.getXXResourceDef().findByServiceDefId(serviceDefId);
		List<XXAccessTypeDef> xxAccessTypes = daoMgr.getXXAccessTypeDef().findByServiceDefId(serviceDefId);
		List<XXPolicyConditionDef> xxPolicyConditions = daoMgr.getXXPolicyConditionDef().findByServiceDefId(
				serviceDefId);
		List<XXContextEnricherDef> xxContextEnrichers = daoMgr.getXXContextEnricherDef().findByServiceDefId(
				serviceDefId);
		List<XXEnumDef> xxEnums = daoMgr.getXXEnumDef().findByServiceDefId(serviceDefId);

		XXServiceConfigDefDao xxServiceConfigDao = daoMgr.getXXServiceConfigDef();
		for (RangerServiceConfigDef config : configs) {
			boolean found = false;
			for (XXServiceConfigDef xConfig : xxConfigs) {
				if (config.getItemId() != null && config.getItemId().equals(xConfig.getItemId())) {
					found = true;
					xConfig = serviceDefService.populateRangerServiceConfigDefToXX(config, xConfig, createdSvcDef,
							RangerServiceDefService.OPERATION_UPDATE_CONTEXT);
					xConfig = xxServiceConfigDao.update(xConfig);
					config = serviceDefService.populateXXToRangerServiceConfigDef(xConfig);
					break;
				}
			}
			if (!found) {
				XXServiceConfigDef xConfig = new XXServiceConfigDef();
				xConfig = serviceDefService.populateRangerServiceConfigDefToXX(config, xConfig, createdSvcDef,
						RangerServiceDefService.OPERATION_CREATE_CONTEXT);
				xConfig = xxServiceConfigDao.create(xConfig);
				config = serviceDefService.populateXXToRangerServiceConfigDef(xConfig);
			}
		}
		for (XXServiceConfigDef xConfig : xxConfigs) {
			boolean found = false;
			for (RangerServiceConfigDef config : configs) {
				if (xConfig.getItemId() != null && xConfig.getItemId().equals(config.getItemId())) {
					found = true;
					break;
				}
			}
			if (!found) {
				xxServiceConfigDao.remove(xConfig);
			}
		}

		XXResourceDefDao xxResDefDao = daoMgr.getXXResourceDef();
		for (RangerResourceDef resource : resources) {
			boolean found = false;
			for (XXResourceDef xRes : xxResources) {
				if (resource.getItemId() != null && resource.getItemId().equals(xRes.getItemId())) {
					found = true;
					xRes = serviceDefService.populateRangerResourceDefToXX(resource, xRes, createdSvcDef,
							RangerServiceDefService.OPERATION_UPDATE_CONTEXT);
					xxResDefDao.update(xRes);
					resource = serviceDefService.populateXXToRangerResourceDef(xRes);
					break;
				}
			}
			if (!found) {
				XXResourceDef parent = xxResDefDao.findByNameAndServiceDefId(resource.getParent(), serviceDefId);
				Long parentId = (parent != null) ? parent.getId() : null;

				XXResourceDef xResource = new XXResourceDef();
				xResource = serviceDefService.populateRangerResourceDefToXX(resource, xResource, createdSvcDef,
						RangerServiceDefService.OPERATION_CREATE_CONTEXT);
				xResource.setParent(parentId);
				xResource = xxResDefDao.create(xResource);
			}
		}
		for (XXResourceDef xRes : xxResources) {
			boolean found = false;
			for (RangerResourceDef resource : resources) {
				if (xRes.getItemId() != null && xRes.getItemId().equals(resource.getItemId())) {
					found = true;
					break;
				}
			}
			if (!found) {
				List<XXPolicyResource> policyResList = daoMgr.getXXPolicyResource().findByResDefId(xRes.getId());
				if (!stringUtil.isEmpty(policyResList)) {
					throw restErrorUtil.createRESTException("Policy/Policies are referring to this resource: "
							+ xRes.getName() + ". Please remove such references from policy before updating service-def.",
							MessageEnums.DATA_NOT_UPDATABLE);
				}
				deleteXXResourceDef(xRes);
			}
		}

		XXAccessTypeDefDao xxATDDao = daoMgr.getXXAccessTypeDef();
		for (RangerAccessTypeDef access : accessTypes) {
			boolean found = false;
			for (XXAccessTypeDef xAccess : xxAccessTypes) {
				if (access.getItemId() != null && access.getItemId().equals(xAccess.getItemId())) {
					found = true;
					xAccess = serviceDefService.populateRangerAccessTypeDefToXX(access, xAccess, createdSvcDef,
							RangerServiceDefService.OPERATION_UPDATE_CONTEXT);
					xAccess = xxATDDao.update(xAccess);

					Collection<String> impliedGrants = access.getImpliedGrants();
					XXAccessTypeDefGrantsDao xxATDGrantDao = daoMgr.getXXAccessTypeDefGrants();
					List<String> xxImpliedGrants = xxATDGrantDao.findImpliedGrantsByATDId(xAccess.getId());
					for (String impliedGrant : impliedGrants) {
						boolean foundGrant = false;
						for (String xImpliedGrant : xxImpliedGrants) {
							if (StringUtils.equalsIgnoreCase(impliedGrant, xImpliedGrant)) {
								foundGrant = true;
								break;
							}
						}
						if (!foundGrant) {
							XXAccessTypeDefGrants xImpliedGrant = new XXAccessTypeDefGrants();
							xImpliedGrant.setAtdId(xAccess.getId());
							xImpliedGrant.setImpliedGrant(impliedGrant);
							xImpliedGrant = xxATDGrantDao.create(xImpliedGrant);
						}
					}
					for (String xImpliedGrant : xxImpliedGrants) {
						boolean foundGrant = false;
						for (String impliedGrant : impliedGrants) {
							if (StringUtils.equalsIgnoreCase(xImpliedGrant, impliedGrant)) {
								foundGrant = true;
								break;
							}
						}
						if (!foundGrant) {
							XXAccessTypeDefGrants xATDGrant = xxATDGrantDao.findByNameAndATDId(xAccess.getId(),
									xImpliedGrant);
							xxATDGrantDao.remove(xATDGrant);

						}
					}
					access = serviceDefService.populateXXToRangerAccessTypeDef(xAccess);
					break;
				}
			}
			if (!found) {
				XXAccessTypeDef xAccessType = new XXAccessTypeDef();
				xAccessType = serviceDefService.populateRangerAccessTypeDefToXX(access, xAccessType, createdSvcDef,
						RangerServiceDefService.OPERATION_CREATE_CONTEXT);
				xAccessType = xxATDDao.create(xAccessType);

				Collection<String> impliedGrants = access.getImpliedGrants();
				XXAccessTypeDefGrantsDao xxATDGrantDao = daoMgr.getXXAccessTypeDefGrants();
				for (String impliedGrant : impliedGrants) {
					XXAccessTypeDefGrants xImpliedGrant = new XXAccessTypeDefGrants();
					xImpliedGrant.setAtdId(xAccessType.getId());
					xImpliedGrant.setImpliedGrant(impliedGrant);
					xImpliedGrant = xxATDGrantDao.create(xImpliedGrant);
				}
				access = serviceDefService.populateXXToRangerAccessTypeDef(xAccessType);
			}
		}

		for (XXAccessTypeDef xAccess : xxAccessTypes) {
			boolean found = false;
			for (RangerAccessTypeDef access : accessTypes) {
				if (xAccess.getItemId() != null && xAccess.getItemId().equals(access.getItemId())) {
					found = true;
					break;
				}
			}
			if (!found) {
				List<XXPolicyItemAccess> polItemAccessList = daoMgr.getXXPolicyItemAccess().findByType(xAccess.getId());
				if(!stringUtil.isEmpty(polItemAccessList)) {
					throw restErrorUtil.createRESTException("Policy/Policies are referring to this access-type: "
							+ xAccess.getName() + ". Please remove such references from policy before updating service-def.",
							MessageEnums.DATA_NOT_UPDATABLE);
				}
				deleteXXAccessTypeDef(xAccess);
			}
		}

		XXPolicyConditionDefDao xxPolCondDao = daoMgr.getXXPolicyConditionDef();
		for (RangerPolicyConditionDef condition : policyConditions) {
			boolean found = false;
			for (XXPolicyConditionDef xCondition : xxPolicyConditions) {
				if (condition.getItemId() != null && condition.getItemId().equals(xCondition.getItemId())) {
					found = true;
					xCondition = serviceDefService.populateRangerPolicyConditionDefToXX(condition, xCondition,
							createdSvcDef, RangerServiceDefService.OPERATION_UPDATE_CONTEXT);
					xCondition = xxPolCondDao.update(xCondition);
					condition = serviceDefService.populateXXToRangerPolicyConditionDef(xCondition);
					break;
				}
			}
			if (!found) {
				XXPolicyConditionDef xCondition = new XXPolicyConditionDef();
				xCondition = serviceDefService.populateRangerPolicyConditionDefToXX(condition, xCondition,
						createdSvcDef, RangerServiceDefService.OPERATION_CREATE_CONTEXT);
				xCondition = xxPolCondDao.create(xCondition);
				condition = serviceDefService.populateXXToRangerPolicyConditionDef(xCondition);
			}
		}
		for(XXPolicyConditionDef xCondition : xxPolicyConditions) {
			boolean found = false;
			for(RangerPolicyConditionDef condition : policyConditions) {
				if(xCondition.getItemId() != null && xCondition.getItemId().equals(condition.getItemId())) {
					found = true;
					break;
				}
			}
			if(!found) {
				List<XXPolicyItemCondition> policyItemCondList = daoMgr.getXXPolicyItemCondition()
						.findByPolicyConditionDefId(xCondition.getId());
				if(!stringUtil.isEmpty(policyItemCondList)) {
					throw restErrorUtil.createRESTException("Policy/Policies are referring to this policy-condition: "
							+ xCondition.getName() + ". Please remove such references from policy before updating service-def.",
							MessageEnums.DATA_NOT_UPDATABLE);
				}
				for(XXPolicyItemCondition policyItemCond : policyItemCondList) {
					daoMgr.getXXPolicyItemCondition().remove(policyItemCond);
				}
				xxPolCondDao.remove(xCondition);
			}
		}

		XXContextEnricherDefDao xxContextEnricherDao = daoMgr.getXXContextEnricherDef();
		for (RangerContextEnricherDef context : contextEnrichers) {
			boolean found = false;
			for (XXContextEnricherDef xContext : xxContextEnrichers) {
				if (context.getItemId() != null && context.getItemId().equals(xContext.getItemId())) {
					found = true;
					xContext = serviceDefService.populateRangerContextEnricherDefToXX(context, xContext, createdSvcDef,
							RangerServiceDefService.OPERATION_UPDATE_CONTEXT);
					xContext = xxContextEnricherDao.update(xContext);
					context = serviceDefService.populateXXToRangerContextEnricherDef(xContext);
					break;
				}
			}
			if (!found) {
				XXContextEnricherDef xContext = new XXContextEnricherDef();
				xContext = serviceDefService.populateRangerContextEnricherDefToXX(context, xContext, createdSvcDef,
						RangerServiceDefService.OPERATION_UPDATE_CONTEXT);
				xContext = xxContextEnricherDao.create(xContext);
				context = serviceDefService.populateXXToRangerContextEnricherDef(xContext);
			}
		}
		for (XXContextEnricherDef xContext : xxContextEnrichers) {
			boolean found = false;
			for (RangerContextEnricherDef context : contextEnrichers) {
				if (xContext.getItemId() != null && xContext.getItemId().equals(context.getItemId())) {
					found = true;
					break;
				}
			}
			if (!found) {
				daoMgr.getXXContextEnricherDef().remove(xContext);
			}
		}

		XXEnumDefDao xxEnumDefDao = daoMgr.getXXEnumDef();
		for (RangerEnumDef enumDef : enums) {
			boolean found = false;
			for (XXEnumDef xEnumDef : xxEnums) {
				if (enumDef.getItemId() != null && enumDef.getItemId().equals(xEnumDef.getItemId())) {
					found = true;
					xEnumDef = serviceDefService.populateRangerEnumDefToXX(enumDef, xEnumDef, createdSvcDef,
							RangerServiceDefService.OPERATION_UPDATE_CONTEXT);
					xEnumDef = xxEnumDefDao.update(xEnumDef);

					XXEnumElementDefDao xEnumEleDao = daoMgr.getXXEnumElementDef();
					List<XXEnumElementDef> xxEnumEleDefs = xEnumEleDao.findByEnumDefId(xEnumDef.getId());
					List<RangerEnumElementDef> enumEleDefs = enumDef.getElements();

					for (RangerEnumElementDef eleDef : enumEleDefs) {
						boolean foundEle = false;
						for (XXEnumElementDef xEleDef : xxEnumEleDefs) {
							if (eleDef.getItemId() != null && eleDef.getItemId().equals(xEleDef.getItemId())) {
								foundEle = true;
								xEleDef = serviceDefService.populateRangerEnumElementDefToXX(eleDef, xEleDef, xEnumDef,
										RangerServiceDefService.OPERATION_UPDATE_CONTEXT);
								xEleDef = xEnumEleDao.update(xEleDef);
								break;
							}
						}
						if (!foundEle) {
							XXEnumElementDef xElement = new XXEnumElementDef();
							xElement = serviceDefService.populateRangerEnumElementDefToXX(eleDef, xElement, xEnumDef,
									RangerServiceDefService.OPERATION_CREATE_CONTEXT);
							xElement = xEnumEleDao.create(xElement);
						}
					}
					for (XXEnumElementDef xxEleDef : xxEnumEleDefs) {
						boolean foundEle = false;
						for (RangerEnumElementDef enumEle : enumEleDefs) {
							if (xxEleDef.getItemId() != null && xxEleDef.getItemId().equals(enumEle.getItemId())) {
								foundEle = true;
								break;
							}
						}
						if (!foundEle) {
							xEnumEleDao.remove(xxEleDef);
						}
					}
					enumDef = serviceDefService.populateXXToRangerEnumDef(xEnumDef);
					break;
				}
			}
			if (!found) {
				XXEnumDef xEnum = new XXEnumDef();
				xEnum = serviceDefService.populateRangerEnumDefToXX(enumDef, xEnum, createdSvcDef,
						RangerServiceDefService.OPERATION_CREATE_CONTEXT);
				xEnum = xxEnumDefDao.create(xEnum);

				List<RangerEnumElementDef> elements = enumDef.getElements();
				XXEnumElementDefDao xxEnumEleDefDao = daoMgr.getXXEnumElementDef();
				for (RangerEnumElementDef element : elements) {
					XXEnumElementDef xElement = new XXEnumElementDef();
					xElement = serviceDefService.populateRangerEnumElementDefToXX(element, xElement, xEnum,
							RangerServiceDefService.OPERATION_CREATE_CONTEXT);
					xElement = xxEnumEleDefDao.create(xElement);
				}
				enumDef = serviceDefService.populateXXToRangerEnumDef(xEnum);
			}
		}
		for (XXEnumDef xEnumDef : xxEnums) {
			boolean found = false;
			for (RangerEnumDef enumDef : enums) {
				if (xEnumDef.getItemId() != null && xEnumDef.getItemId().equals(enumDef.getItemId())) {
					found = true;
					break;
				}
			}
			if (!found) {
				List<XXEnumElementDef> enumEleDefList = daoMgr.getXXEnumElementDef().findByEnumDefId(xEnumDef.getId());
				for (XXEnumElementDef eleDef : enumEleDefList) {
					daoMgr.getXXEnumElementDef().remove(eleDef);
				}
				xxEnumDefDao.remove(xEnumDef);
			}
		}

		List<RangerDataMaskTypeDef> dataMasks            = dataMaskDef == null || dataMaskDef.getMaskTypes() == null ? new ArrayList<RangerDataMaskTypeDef>() : dataMaskDef.getMaskTypes();
		List<String>                supportedAccessTypes = dataMaskDef == null || dataMaskDef.getSupportedAccessTypes() == null ? new ArrayList<String>() : dataMaskDef.getSupportedAccessTypes();
		List<String>                supportedResources   = dataMaskDef == null || dataMaskDef.getSupportedResources() == null ? new ArrayList<String>() : dataMaskDef.getSupportedResources();
		XXDataMaskTypeDefDao        dataMaskTypeDao      = daoMgr.getXXDataMaskTypeDef();
		List<XXDataMaskTypeDef>     xxDataMaskTypes      = dataMaskTypeDao.findByServiceDefId(serviceDefId);
		// create or update dataMasks
		for (RangerServiceDef.RangerDataMaskTypeDef dataMask : dataMasks) {
			boolean found = false;
			for (XXDataMaskTypeDef xxDataMask : xxDataMaskTypes) {
				if (xxDataMask.getItemId() != null && xxDataMask.getItemId().equals(dataMask.getItemId())) {
					if (LOG.isDebugEnabled()) {
						LOG.debug("Updating existing dataMask with itemId=" + dataMask.getItemId());
					}

					found = true;
					xxDataMask = serviceDefService.populateRangerDataMaskDefToXX(dataMask, xxDataMask, createdSvcDef,
							RangerServiceDefService.OPERATION_UPDATE_CONTEXT);
					xxDataMask = dataMaskTypeDao.update(xxDataMask);
					dataMask = serviceDefService.populateXXToRangerDataMaskTypeDef(xxDataMask);
					break;
				}
			}

			if (!found) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Creating dataMask with itemId=" + dataMask.getItemId() + "");
				}

				XXDataMaskTypeDef xxDataMask = new XXDataMaskTypeDef();
				xxDataMask = serviceDefService.populateRangerDataMaskDefToXX(dataMask, xxDataMask, createdSvcDef, RangerServiceDefService.OPERATION_CREATE_CONTEXT);
				xxDataMask = dataMaskTypeDao.create(xxDataMask);
			}
		}

		// remove dataMasks
		for (XXDataMaskTypeDef xxDataMask : xxDataMaskTypes) {
			boolean found = false;
			for (RangerDataMaskTypeDef dataMask : dataMasks) {
				if (xxDataMask.getItemId() != null && xxDataMask.getItemId().equals(dataMask.getItemId())) {
					found = true;
					break;
				}
			}
			if (!found) {
				if(LOG.isDebugEnabled()) {
					LOG.debug("Deleting dataMask with itemId=" + xxDataMask.getItemId());
				}

				dataMaskTypeDao.remove(xxDataMask);
			}
		}

		List<XXAccessTypeDef> xxAccessTypeDefs = xxATDDao.findByServiceDefId(serviceDefId);

		for(String accessType : supportedAccessTypes) {
			boolean found = false;
			for(XXAccessTypeDef xxAccessTypeDef : xxAccessTypeDefs) {
				if(StringUtils.equals(xxAccessTypeDef.getName(), accessType)) {
					found = true;
					break;
				}
			}

			if(! found) {
				throw restErrorUtil.createRESTException("accessType with name: "
						+ accessType + " does not exists", MessageEnums.DATA_NOT_FOUND);
			}
		}

		for(XXAccessTypeDef xxAccessTypeDef : xxAccessTypeDefs) {
			boolean isDatamaskingSupported = supportedAccessTypes.contains(xxAccessTypeDef.getName());

			if(xxAccessTypeDef.isDatamaskingSupported() != isDatamaskingSupported) {
				xxAccessTypeDef.setDatamaskingSupported(isDatamaskingSupported);
				xxATDDao.update(xxAccessTypeDef);
			}
		}

		List<XXResourceDef> xxResourceDefs = xxResDefDao.findByServiceDefId(serviceDefId);

		for(String resource : supportedResources) {
			boolean found = false;
			for(XXResourceDef xxResourceDef : xxResourceDefs) {
				if(StringUtils.equals(xxResourceDef.getName(), resource)) {
					found = true;
					break;
				}
			}

			if(! found) {
				throw restErrorUtil.createRESTException("resource with name: "
						+ resource + " does not exists", MessageEnums.DATA_NOT_FOUND);
			}
		}

		for(XXResourceDef xxResourceDef : xxResourceDefs) {
			boolean isDatamaskingSupported = supportedResources.contains(xxResourceDef.getName());

			if(xxResourceDef.isDatamaskingSupported() != isDatamaskingSupported) {
				xxResourceDef.setDatamaskingSupported(isDatamaskingSupported);
				xxResDefDao.update(xxResourceDef);
			}
		}
	}

	@Override
	public void deleteServiceDef(Long serviceDefId) throws Exception {

		UserSessionBase session = ContextUtil.getCurrentUserSession();
		if (session == null) {
			throw restErrorUtil.createRESTException(
					"UserSession cannot be null, only Admin can update service-def",
					MessageEnums.OPER_NO_PERMISSION);
		}

		if (!session.isKeyAdmin() && !session.isUserAdmin()) {
			throw restErrorUtil.createRESTException(
					"User is not allowed to update service-def, only Admin can update service-def",
					MessageEnums.OPER_NO_PERMISSION);
		}

		deleteServiceDef(serviceDefId, false);
	}

	public void deleteServiceDef(Long serviceDefId, Boolean forceDelete) throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDefDBStore.deleteServiceDef(" + serviceDefId + ")");
		}

		RangerServiceDef serviceDef = getServiceDef(serviceDefId);
		if(serviceDef == null) {
			throw restErrorUtil.createRESTException("No Service Definiton found for Id: " + serviceDefId,
					MessageEnums.DATA_NOT_FOUND);
		}
		
		List<XXService> serviceList = daoMgr.getXXService().findByServiceDefId(serviceDefId);
		if (!forceDelete) {
			if(CollectionUtils.isNotEmpty(serviceList)) {
				throw restErrorUtil.createRESTException(
						"Services exists under given service definition, can't delete Service-Def: "
								+ serviceDef.getName(), MessageEnums.OPER_NOT_ALLOWED_FOR_ENTITY);
			}
		}

		XXDataMaskTypeDefDao dataMaskDao = daoMgr.getXXDataMaskTypeDef();
		List<XXDataMaskTypeDef> dataMaskDefs = dataMaskDao.findByServiceDefId(serviceDefId);
		for(XXDataMaskTypeDef dataMaskDef : dataMaskDefs) {
			dataMaskDao.remove(dataMaskDef);
		}

		List<XXAccessTypeDef> accTypeDefs = daoMgr.getXXAccessTypeDef().findByServiceDefId(serviceDefId);
		for(XXAccessTypeDef accessType : accTypeDefs) {
			deleteXXAccessTypeDef(accessType);
		}
		
		XXContextEnricherDefDao xContextEnricherDao = daoMgr.getXXContextEnricherDef();
		List<XXContextEnricherDef> contextEnrichers = xContextEnricherDao.findByServiceDefId(serviceDefId);
		for(XXContextEnricherDef context : contextEnrichers) {
			xContextEnricherDao.remove(context);
		}
		
		XXEnumDefDao enumDefDao = daoMgr.getXXEnumDef();
		List<XXEnumDef> enumDefList = enumDefDao.findByServiceDefId(serviceDefId);
		for (XXEnumDef enumDef : enumDefList) {
			List<XXEnumElementDef> enumEleDefList = daoMgr.getXXEnumElementDef().findByEnumDefId(enumDef.getId());
			for (XXEnumElementDef eleDef : enumEleDefList) {
				daoMgr.getXXEnumElementDef().remove(eleDef);
			}
			enumDefDao.remove(enumDef);
		}
		
		XXPolicyConditionDefDao policyCondDao = daoMgr.getXXPolicyConditionDef();
		List<XXPolicyConditionDef> policyCondList = policyCondDao.findByServiceDefId(serviceDefId);
		
		for (XXPolicyConditionDef policyCond : policyCondList) {
			List<XXPolicyItemCondition> policyItemCondList = daoMgr.getXXPolicyItemCondition().findByPolicyConditionDefId(policyCond.getId());
			for (XXPolicyItemCondition policyItemCond : policyItemCondList) {
				daoMgr.getXXPolicyItemCondition().remove(policyItemCond);
			}
			policyCondDao.remove(policyCond);
		}
		
		List<XXResourceDef> resDefList = daoMgr.getXXResourceDef().findByServiceDefId(serviceDefId);
		for(XXResourceDef resDef : resDefList) {
			deleteXXResourceDef(resDef);
		}
		
		XXServiceConfigDefDao configDefDao = daoMgr.getXXServiceConfigDef();
		List<XXServiceConfigDef> configDefList = configDefDao.findByServiceDefId(serviceDefId);
		for(XXServiceConfigDef configDef : configDefList) {
			configDefDao.remove(configDef);
		}
		
		if(CollectionUtils.isNotEmpty(serviceList)) {
			for(XXService service : serviceList) {
				deleteService(service.getId());
			}
		}
		
		Long version = serviceDef.getVersion();
		if(version == null) {
			version = Long.valueOf(1);
			LOG.info("Found Version Value: `null`, so setting value of version to 1, While updating object, version should not be null.");
		} else {
			version = Long.valueOf(version.longValue() + 1);
		}
		serviceDef.setVersion(version);
		
		serviceDefService.delete(serviceDef);
		LOG.info("ServiceDefinition has been deleted successfully. Service-Def Name: " + serviceDef.getName());
		
		dataHistService.createObjectDataHistory(serviceDef, RangerDataHistService.ACTION_DELETE);

		postDelete(serviceDef);

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDefDBStore.deleteServiceDef(" + serviceDefId + ")");
		}
	}

	public void deleteXXAccessTypeDef(XXAccessTypeDef xAccess) {
		List<XXAccessTypeDefGrants> atdGrantsList = daoMgr.getXXAccessTypeDefGrants().findByATDId(xAccess.getId());

		for (XXAccessTypeDefGrants atdGrant : atdGrantsList) {
			daoMgr.getXXAccessTypeDefGrants().remove(atdGrant);
		}

		List<XXPolicyItemAccess> policyItemAccessList = daoMgr.getXXPolicyItemAccess().findByType(xAccess.getId());
		for (XXPolicyItemAccess policyItemAccess : policyItemAccessList) {
			daoMgr.getXXPolicyItemAccess().remove(policyItemAccess);
		}
		daoMgr.getXXAccessTypeDef().remove(xAccess);
	}

	public void deleteXXResourceDef(XXResourceDef xRes) {

		List<XXResourceDef> xChildObjs = daoMgr.getXXResourceDef().findByParentResId(xRes.getId());
		for(XXResourceDef childRes : xChildObjs) {
			deleteXXResourceDef(childRes);
		}

		List<XXPolicyResource> xxResources = daoMgr.getXXPolicyResource().findByResDefId(xRes.getId());
		for (XXPolicyResource xPolRes : xxResources) {
			deleteXXPolicyResource(xPolRes);
		}

		daoMgr.getXXResourceDef().remove(xRes);
	}

	public void deleteXXPolicyResource(XXPolicyResource xPolRes) {
		List<XXPolicyResourceMap> polResMapList = daoMgr.getXXPolicyResourceMap().findByPolicyResId(xPolRes.getId());
		XXPolicyResourceMapDao polResMapDao = daoMgr.getXXPolicyResourceMap();
		for (XXPolicyResourceMap xxPolResMap : polResMapList) {
			polResMapDao.remove(xxPolResMap);
		}
		daoMgr.getXXPolicyResource().remove(xPolRes);
	}

	@Override
	public RangerServiceDef getServiceDef(Long id) throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDefDBStore.getServiceDef(" + id + ")");
		}

		RangerServiceDef ret = serviceDefService.read(id);
		if (LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDefDBStore.getServiceDef(" + id + "): " + ret);
		}

		return ret;
	}

	@Override
	public RangerServiceDef getServiceDefByName(String name) throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDefDBStore.getServiceDefByName(" + name + ")");
		}

		RangerServiceDef ret = null;

		XXServiceDef xServiceDef = daoMgr.getXXServiceDef().findByName(name);

		if(xServiceDef != null) {
			ret = serviceDefService.getPopulatedViewObject(xServiceDef);
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug("== ServiceDefDBStore.getServiceDefByName(" + name + "): " + ret);
		}

		return  ret;
	}

	@Override
	public List<RangerServiceDef> getServiceDefs(SearchFilter filter) throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.getServiceDefs(" + filter + ")");
		}

		RangerServiceDefList svcDefList = serviceDefService.searchRangerServiceDefs(filter);

		predicateUtil.applyFilter(svcDefList.getServiceDefs(), filter);

		List<RangerServiceDef> ret = svcDefList.getServiceDefs();

		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.getServiceDefs(" + filter + "): " + ret);
		}

		return ret;
	}

	@Override

	public PList<RangerServiceDef> getPaginatedServiceDefs(SearchFilter filter) throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.getPaginatedServiceDefs(" + filter + ")");
		}

		RangerServiceDefList svcDefList = serviceDefService.searchRangerServiceDefs(filter);

		predicateUtil.applyFilter(svcDefList.getServiceDefs(), filter);

		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.getPaginatedServiceDefs(" + filter + ")");
		}

		return new PList<RangerServiceDef>(svcDefList.getServiceDefs(), svcDefList.getStartIndex(), svcDefList.getPageSize(), svcDefList.getTotalCount(),
				svcDefList.getResultSize(), svcDefList.getSortType(), svcDefList.getSortBy());

	}

	@Override
	public RangerService createService(RangerService service) throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDefDBStore.createService(" + service + ")");
		}

		if (service == null) {
			throw restErrorUtil.createRESTException("Service object cannot be null.",
					MessageEnums.ERROR_CREATING_OBJECT);
		}

		boolean createDefaultPolicy = true;
		Map<String, String> configs = service.getConfigs();
		Map<String, String> validConfigs = validateRequiredConfigParams(service, configs);
		if (validConfigs == null) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("==> ConfigParams cannot be null, ServiceDefDBStore.createService(" + service + ")");
			}
			throw restErrorUtil.createRESTException("ConfigParams cannot be null.", MessageEnums.ERROR_CREATING_OBJECT);
		}

		// While creating, value of version should be 1.
		service.setVersion(Long.valueOf(1));
		service.setTagVersion(Long.valueOf(1));

		if (populateExistingBaseFields) {
			svcServiceWithAssignedId.setPopulateExistingBaseFields(true);
			daoMgr.getXXService().setIdentityInsert(true);

			service = svcServiceWithAssignedId.create(service);

			daoMgr.getXXService().setIdentityInsert(false);
			daoMgr.getXXService().updateSequence();
			svcServiceWithAssignedId.setPopulateExistingBaseFields(false);
			createDefaultPolicy = false;
		} else {
			service = svcService.create(service);
		}
		XXService xCreatedService = daoMgr.getXXService().getById(service.getId());
		VXUser vXUser = null;

		XXServiceConfigMapDao xConfMapDao = daoMgr.getXXServiceConfigMap();
		for (Entry<String, String> configMap : validConfigs.entrySet()) {
			String configKey = configMap.getKey();
			String configValue = configMap.getValue();

			if (StringUtils.equalsIgnoreCase(configKey, "username")) {
				String userName = stringUtil.getValidUserName(configValue);
				XXUser xxUser = daoMgr.getXXUser().findByUserName(userName);
				if (xxUser != null) {
					vXUser = xUserService.populateViewBean(xxUser);
				} else {
					vXUser = new VXUser();
					vXUser.setName(userName);
					vXUser.setUserSource(RangerCommonEnums.USER_EXTERNAL);

					UserSessionBase usb = ContextUtil.getCurrentUserSession();
					if (usb != null && !usb.isUserAdmin()) {
						throw restErrorUtil.createRESTException("User does not exist with given username: ["
								+ userName + "] please use existing user", MessageEnums.OPER_NO_PERMISSION);
					}
					vXUser = xUserMgr.createXUser(vXUser);
				}
			}

			if (StringUtils.equalsIgnoreCase(configKey, CONFIG_KEY_PASSWORD)) {
				String encryptedPwd = PasswordUtils.encryptPassword(configValue);
				String decryptedPwd = PasswordUtils.decryptPassword(encryptedPwd);

				if (StringUtils.equals(decryptedPwd, configValue)) {
					configValue = encryptedPwd;
				}
			}

			XXServiceConfigMap xConfMap = new XXServiceConfigMap();
			xConfMap = (XXServiceConfigMap) rangerAuditFields.populateAuditFields(xConfMap, xCreatedService);
			xConfMap.setServiceId(xCreatedService.getId());
			xConfMap.setConfigkey(configKey);
			xConfMap.setConfigvalue(configValue);
			xConfMap = xConfMapDao.create(xConfMap);
		}
		RangerService createdService = svcService.getPopulatedViewObject(xCreatedService);

		if (createdService == null) {
			throw restErrorUtil.createRESTException("Could not create service - Internal error ", MessageEnums.ERROR_CREATING_OBJECT);
		}

		dataHistService.createObjectDataHistory(createdService, RangerDataHistService.ACTION_CREATE);

		List<XXTrxLog> trxLogList = svcService.getTransactionLog(createdService,
				RangerServiceService.OPERATION_CREATE_CONTEXT);
		bizUtil.createTrxLog(trxLogList);

		if (createDefaultPolicy) {
			createDefaultPolicies(xCreatedService, vXUser);
		}

		return createdService;

	}

	@Override
	public RangerService updateService(RangerService service) throws Exception {
		if(LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.updateService()");
		}

		XXService existing = daoMgr.getXXService().getById(service.getId());

		if(existing == null) {
			throw restErrorUtil.createRESTException(
					"no service exists with ID=" + service.getId(),
					MessageEnums.DATA_NOT_FOUND);
		}

		String existingName = existing.getName();

		boolean renamed = !StringUtils.equalsIgnoreCase(service.getName(), existingName);

		if(renamed) {
			XXService newNameService = daoMgr.getXXService().findByName(service.getName());

			if(newNameService != null) {
				throw restErrorUtil.createRESTException("another service already exists with name '"
						+ service.getName() + "'. ID=" + newNameService.getId(), MessageEnums.DATA_NOT_UPDATABLE);
			}
		}

		Map<String, String> configs = service.getConfigs();
		Map<String, String> validConfigs = validateRequiredConfigParams(service, configs);
		if (validConfigs == null) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("==> ConfigParams cannot be null, ServiceDefDBStore.createService(" + service + ")");
			}
			throw restErrorUtil.createRESTException("ConfigParams cannot be null.", MessageEnums.ERROR_CREATING_OBJECT);
		}

		List<XXTrxLog> trxLogList = svcService.getTransactionLog(service, existing, RangerServiceService.OPERATION_UPDATE_CONTEXT);

		boolean hasTagServiceValueChanged = false;
		Long    existingTagServiceId      = existing.getTagService();
		String  newTagServiceName         = service.getTagService(); // null for old clients; empty string to remove existing association
		Long    newTagServiceId           = null;

		if(newTagServiceName == null) { // old client; don't update existing tagService
			if(existingTagServiceId != null) {
				newTagServiceName = getServiceName(existingTagServiceId);

				service.setTagService(newTagServiceName);

				LOG.info("ServiceDBStore.updateService(id=" + service.getId() + "; name=" + service.getName() + "): tagService is null; using existing tagService '" + newTagServiceName + "'");
			}
		}

		if (StringUtils.isNotBlank(newTagServiceName)) {
			RangerService tmp = getServiceByName(newTagServiceName);

			if (tmp == null || !tmp.getType().equals(EmbeddedServiceDefsUtil.EMBEDDED_SERVICEDEF_TAG_NAME)) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("ServiceDBStore.updateService() - " + newTagServiceName + " does not refer to a valid tag service.(" + service + ")");
				}
				throw restErrorUtil.createRESTException("Invalid tag service name " + newTagServiceName, MessageEnums.ERROR_CREATING_OBJECT);

			} else {
				newTagServiceId = tmp.getId();
			}
		}

		if (existingTagServiceId == null) {
			if (newTagServiceId != null) {
				hasTagServiceValueChanged = true;
			}
		} else if (!existingTagServiceId.equals(newTagServiceId)) {
			hasTagServiceValueChanged = true;
		}

		boolean hasIsEnabledChanged = !existing.getIsenabled().equals(service.getIsEnabled());

		if(populateExistingBaseFields) {
			svcServiceWithAssignedId.setPopulateExistingBaseFields(true);
			service = svcServiceWithAssignedId.update(service);
			svcServiceWithAssignedId.setPopulateExistingBaseFields(false);
		} else {
			service.setCreateTime(existing.getCreateTime());
			service.setGuid(existing.getGuid());
			service.setVersion(existing.getVersion());
			service.setPolicyUpdateTime(existing.getPolicyUpdateTime());
			service.setPolicyVersion(existing.getPolicyVersion());
			service.setTagVersion(existing.getTagVersion());
			service.setTagUpdateTime(existing.getTagUpdateTime());

			service = svcService.update(service);

			if (hasTagServiceValueChanged || hasIsEnabledChanged) {
				updatePolicyVersion(service);
			}
		}

		XXService xUpdService = daoMgr.getXXService().getById(service.getId());

		String oldPassword = null;

		List<XXServiceConfigMap> dbConfigMaps = daoMgr.getXXServiceConfigMap().findByServiceId(service.getId());
		for(XXServiceConfigMap dbConfigMap : dbConfigMaps) {
			if(StringUtils.equalsIgnoreCase(dbConfigMap.getConfigkey(), CONFIG_KEY_PASSWORD)) {
				oldPassword = dbConfigMap.getConfigvalue();
			}
			daoMgr.getXXServiceConfigMap().remove(dbConfigMap);
		}

		VXUser vXUser = null;
		XXServiceConfigMapDao xConfMapDao = daoMgr.getXXServiceConfigMap();
		for (Entry<String, String> configMap : validConfigs.entrySet()) {
			String configKey = configMap.getKey();
			String configValue = configMap.getValue();

			if(StringUtils.equalsIgnoreCase(configKey, "username")) {
				String userName = stringUtil.getValidUserName(configValue);
				XXUser xxUser = daoMgr.getXXUser().findByUserName(userName);
				if (xxUser != null) {
					vXUser = xUserService.populateViewBean(xxUser);
				} else {
					vXUser = new VXUser();
					vXUser.setName(userName);
					vXUser.setUserSource(RangerCommonEnums.USER_EXTERNAL);
					UserSessionBase usb = ContextUtil.getCurrentUserSession();
					if (usb != null && !usb.isUserAdmin()) {
						throw restErrorUtil.createRESTException("User does not exist with given username: ["
								+ userName + "] please use existing user", MessageEnums.OPER_NO_PERMISSION);
					}
					vXUser = xUserMgr.createXUser(vXUser);
				}
			}

			if (StringUtils.equalsIgnoreCase(configKey, CONFIG_KEY_PASSWORD)) {
				if (StringUtils.equalsIgnoreCase(configValue, HIDDEN_PASSWORD_STR)) {
					configValue = oldPassword;
				} else {
					String encryptedPwd = PasswordUtils.encryptPassword(configValue);
					String decryptedPwd = PasswordUtils.decryptPassword(encryptedPwd);

					if (StringUtils.equals(decryptedPwd, configValue)) {
						configValue = encryptedPwd;
					}
				}
			}

			XXServiceConfigMap xConfMap = new XXServiceConfigMap();
			xConfMap = (XXServiceConfigMap) rangerAuditFields.populateAuditFields(xConfMap, xUpdService);
			xConfMap.setServiceId(service.getId());
			xConfMap.setConfigkey(configKey);
			xConfMap.setConfigvalue(configValue);
			xConfMap = xConfMapDao.create(xConfMap);
		}

		RangerService updService = svcService.getPopulatedViewObject(xUpdService);
		dataHistService.createObjectDataHistory(updService, RangerDataHistService.ACTION_UPDATE);
		bizUtil.createTrxLog(trxLogList);

		return updService;
	}

	@Override
	public void deleteService(Long id) throws Exception {
		if(LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.deleteService(" + id + ")");
		}

		RangerService service = getService(id);

		if(service == null) {
			throw new Exception("no service exists with ID=" + id);
		}

		List<XXPolicy> policies = daoMgr.getXXPolicy().findByServiceId(service.getId());
		for(XXPolicy policy : policies) {
			LOG.info("Deleting Policy, policyName: " + policy.getName());
			deletePolicy(policy.getId());
		}

		XXServiceConfigMapDao configDao = daoMgr.getXXServiceConfigMap();
		List<XXServiceConfigMap> configs = configDao.findByServiceId(service.getId());
		for (XXServiceConfigMap configMap : configs) {
			configDao.remove(configMap);
		}

		Long version = service.getVersion();
		if(version == null) {
			version = Long.valueOf(1);
			LOG.info("Found Version Value: `null`, so setting value of version to 1, While updating object, version should not be null.");
		} else {
			version = Long.valueOf(version.longValue() + 1);
		}
		service.setVersion(version);

		svcService.delete(service);

		dataHistService.createObjectDataHistory(service, RangerDataHistService.ACTION_DELETE);

		List<XXTrxLog> trxLogList = svcService.getTransactionLog(service, RangerServiceService.OPERATION_DELETE_CONTEXT);
		bizUtil.createTrxLog(trxLogList);
	}

	@Override
	public List<RangerPolicy> getPoliciesByResourceSignature(String serviceName, String policySignature, Boolean isPolicyEnabled) throws Exception {

		List<XXPolicy> xxPolicies = daoMgr.getXXPolicy().findByResourceSignatureByPolicyStatus(serviceName, policySignature, isPolicyEnabled);
		List<RangerPolicy> policies = new ArrayList<RangerPolicy>(xxPolicies.size());
		for (XXPolicy xxPolicy : xxPolicies) {
			RangerPolicy policy = policyService.getPopulatedViewObject(xxPolicy);
			policies.add(policy);
		}
		
		return policies;
	}

	@Override
	public RangerService getService(Long id) throws Exception {
		if(LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.getService()");
		}

		UserSessionBase session = ContextUtil.getCurrentUserSession();
		if (session == null) {
			throw restErrorUtil.createRESTException("UserSession cannot be null.",
					MessageEnums.OPER_NOT_ALLOWED_FOR_STATE);
		}

		XXService xService = daoMgr.getXXService().getById(id);

		// TODO: As of now we are allowing SYS_ADMIN to read all the
		// services including KMS

		if (!bizUtil.hasAccess(xService, null)) {
			throw restErrorUtil.createRESTException("Logged in user is not allowed to read service, id: " + id,
					MessageEnums.OPER_NO_PERMISSION);
		}

		return svcService.getPopulatedViewObject(xService);
	}

	@Override
	public RangerService getServiceByName(String name) throws Exception {
		if(LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.getServiceByName()");
		}
		XXService xService = daoMgr.getXXService().findByName(name);

		// TODO: As of now we are allowing SYS_ADMIN to read all the
		// services including KMS

		if (ContextUtil.getCurrentUserSession() != null) {
			if (xService == null) {
				return null;
			}
			if (!bizUtil.hasAccess(xService, null)) {
				throw restErrorUtil.createRESTException("Logged in user is not allowed to read service, name: " + name,
						MessageEnums.OPER_NO_PERMISSION);
			}
		}

		return xService == null ? null : svcService.getPopulatedViewObject(xService);
	}

	@Override
	public List<RangerService> getServices(SearchFilter filter) throws Exception {
		if(LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.getServices()");
		}

		RangerServiceList serviceList = svcService.searchRangerServices(filter);

		predicateUtil.applyFilter(serviceList.getServices(), filter);

		List<RangerService> ret = serviceList.getServices();

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDBStore.getServices()");
		}

		return ret;
	}

	public PList<RangerService> getPaginatedServices(SearchFilter filter) throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.getPaginatedServices()");
		}

		RangerServiceList serviceList = svcService.searchRangerServices(filter);

		predicateUtil.applyFilter(serviceList.getServices(), filter);

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDBStore.getPaginatedServices()");
		}

		return new PList<RangerService>(serviceList.getServices(), serviceList.getStartIndex(), serviceList.getPageSize(), serviceList.getTotalCount(),
				serviceList.getResultSize(), serviceList.getSortType(), serviceList.getSortBy());

	}

	@Override
	public RangerPolicy createPolicy(RangerPolicy policy) throws Exception {

		RangerService service = getServiceByName(policy.getService());

		if(service == null) {
			throw new Exception("service does not exist - name=" + policy.getService());
		}

		XXServiceDef xServiceDef = daoMgr.getXXServiceDef().findByName(service.getType());

		if(xServiceDef == null) {
			throw new Exception("service-def does not exist - name=" + service.getType());
		}

		XXPolicy existing = daoMgr.getXXPolicy().findByNameAndServiceId(policy.getName(), service.getId());

		if(existing != null) {
			throw new Exception("policy already exists: ServiceName=" + policy.getService() + "; PolicyName=" + policy.getName() + ". ID=" + existing.getId());
		}

		Map<String, RangerPolicyResource> resources = policy.getResources();
		List<RangerPolicyItem> policyItems     = policy.getPolicyItems();
		List<RangerPolicyItem> denyPolicyItems = policy.getDenyPolicyItems();
		List<RangerPolicyItem> allowExceptions = policy.getAllowExceptions();
		List<RangerPolicyItem> denyExceptions  = policy.getDenyExceptions();
		List<RangerDataMaskPolicyItem> dataMaskItems  = policy.getDataMaskPolicyItems();

		policy.setVersion(Long.valueOf(1));
		updatePolicySignature(policy);

		if(populateExistingBaseFields) {
			assignedIdPolicyService.setPopulateExistingBaseFields(true);
			daoMgr.getXXPolicy().setIdentityInsert(true);

			policy = assignedIdPolicyService.create(policy);

			daoMgr.getXXPolicy().setIdentityInsert(false);
			daoMgr.getXXPolicy().updateSequence();
			assignedIdPolicyService.setPopulateExistingBaseFields(false);
		} else {
			policy = policyService.create(policy);
		}

		XXPolicy xCreatedPolicy = daoMgr.getXXPolicy().getById(policy.getId());

		createNewResourcesForPolicy(policy, xCreatedPolicy, resources);
		createNewPolicyItemsForPolicy(policy, xCreatedPolicy, policyItems, xServiceDef, RangerPolicyItemEvaluator.POLICY_ITEM_TYPE_ALLOW);
		createNewPolicyItemsForPolicy(policy, xCreatedPolicy, denyPolicyItems, xServiceDef, RangerPolicyItemEvaluator.POLICY_ITEM_TYPE_DENY);
		createNewPolicyItemsForPolicy(policy, xCreatedPolicy, allowExceptions, xServiceDef, RangerPolicyItemEvaluator.POLICY_ITEM_TYPE_ALLOW_EXCEPTIONS);
		createNewPolicyItemsForPolicy(policy, xCreatedPolicy, denyExceptions, xServiceDef, RangerPolicyItemEvaluator.POLICY_ITEM_TYPE_DENY_EXCEPTIONS);
		createNewDataMaskPolicyItemsForPolicy(policy, xCreatedPolicy, dataMaskItems, xServiceDef, RangerPolicyItemEvaluator.POLICY_ITEM_TYPE_DATA_MASKING);
		handlePolicyUpdate(service);
		RangerPolicy createdPolicy = policyService.getPopulatedViewObject(xCreatedPolicy);
		dataHistService.createObjectDataHistory(createdPolicy, RangerDataHistService.ACTION_CREATE);

		List<XXTrxLog> trxLogList = policyService.getTransactionLog(createdPolicy, RangerPolicyService.OPERATION_CREATE_CONTEXT);
		bizUtil.createTrxLog(trxLogList);

		return createdPolicy;
	}

	@Override
	public RangerPolicy updatePolicy(RangerPolicy policy) throws Exception {
		if(LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.updatePolicy(" + policy + ")");
		}

		XXPolicy xxExisting = daoMgr.getXXPolicy().getById(policy.getId());
		RangerPolicy existing = policyService.getPopulatedViewObject(xxExisting);

		if(existing == null) {
			throw new Exception("no policy exists with ID=" + policy.getId());
		}

		RangerService service = getServiceByName(policy.getService());

		if(service == null) {
			throw new Exception("service does not exist - name=" + policy.getService());
		}

		XXServiceDef xServiceDef = daoMgr.getXXServiceDef().findByName(service.getType());

		if(xServiceDef == null) {
			throw new Exception("service-def does not exist - name=" + service.getType());
		}

		if(! StringUtils.equalsIgnoreCase(existing.getService(), policy.getService())) {
			throw new Exception("policy id=" + policy.getId() + " already exists in service " + existing.getService() + ". It can not be moved to service " + policy.getService());
		}
		boolean renamed = !StringUtils.equalsIgnoreCase(policy.getName(), existing.getName());

		if(renamed) {
			XXPolicy newNamePolicy = daoMgr.getXXPolicy().findByNameAndServiceId(policy.getName(), service.getId());

			if(newNamePolicy != null) {
				throw new Exception("another policy already exists with name '" + policy.getName() + "'. ID=" + newNamePolicy.getId());
			}
		}
		Map<String, RangerPolicyResource> newResources = policy.getResources();
		List<RangerPolicyItem> policyItems     = policy.getPolicyItems();
		List<RangerPolicyItem> denyPolicyItems = policy.getDenyPolicyItems();
		List<RangerPolicyItem> allowExceptions = policy.getAllowExceptions();
		List<RangerPolicyItem> denyExceptions  = policy.getDenyExceptions();
		List<RangerDataMaskPolicyItem> dataMaskPolicyItems = policy.getDataMaskPolicyItems();
		
		policy.setCreateTime(xxExisting.getCreateTime());
		policy.setGuid(xxExisting.getGuid());
		policy.setVersion(xxExisting.getVersion());

		List<XXTrxLog> trxLogList = policyService.getTransactionLog(policy, xxExisting, RangerPolicyService.OPERATION_UPDATE_CONTEXT);
		
		updatePolicySignature(policy);
		
		policy = policyService.update(policy);
		XXPolicy newUpdPolicy = daoMgr.getXXPolicy().getById(policy.getId());

		deleteExistingPolicyResources(policy);
		deleteExistingPolicyItems(policy);
		
		createNewResourcesForPolicy(policy, newUpdPolicy, newResources);
		createNewPolicyItemsForPolicy(policy, newUpdPolicy, policyItems, xServiceDef, RangerPolicyItemEvaluator.POLICY_ITEM_TYPE_ALLOW);
		createNewPolicyItemsForPolicy(policy, newUpdPolicy, denyPolicyItems, xServiceDef, RangerPolicyItemEvaluator.POLICY_ITEM_TYPE_DENY);
		createNewPolicyItemsForPolicy(policy, newUpdPolicy, allowExceptions, xServiceDef, RangerPolicyItemEvaluator.POLICY_ITEM_TYPE_ALLOW_EXCEPTIONS);
		createNewPolicyItemsForPolicy(policy, newUpdPolicy, denyExceptions, xServiceDef, RangerPolicyItemEvaluator.POLICY_ITEM_TYPE_DENY_EXCEPTIONS);
		createNewDataMaskPolicyItemsForPolicy(policy, newUpdPolicy, dataMaskPolicyItems, xServiceDef, RangerPolicyItemEvaluator.POLICY_ITEM_TYPE_DATA_MASKING);

		handlePolicyUpdate(service);
		RangerPolicy updPolicy = policyService.getPopulatedViewObject(newUpdPolicy);
		dataHistService.createObjectDataHistory(updPolicy, RangerDataHistService.ACTION_UPDATE);
		
		bizUtil.createTrxLog(trxLogList);
		
		return updPolicy;
	}

	@Override
	public void deletePolicy(Long policyId) throws Exception {
		if(LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.deletePolicy(" + policyId + ")");
		}

		RangerPolicy policy = getPolicy(policyId);

		if(policy == null) {
			throw new Exception("no policy exists with ID=" + policyId);
		}

		String policyName = policy.getName();
		RangerService service = getServiceByName(policy.getService());
		
		if(service == null) {
			throw new Exception("service does not exist - name='" + policy.getService());
		}
		
		Long version = policy.getVersion();
		if(version == null) {
			version = Long.valueOf(1);
			LOG.info("Found Version Value: `null`, so setting value of version to 1, While updating object, version should not be null.");
		} else {
			version = Long.valueOf(version.longValue() + 1);
		}
		
		policy.setVersion(version);
		
		List<XXTrxLog> trxLogList = policyService.getTransactionLog(policy, RangerPolicyService.OPERATION_DELETE_CONTEXT);
		
		deleteExistingPolicyItems(policy);
		deleteExistingPolicyResources(policy);
		
		policyService.delete(policy);
		handlePolicyUpdate(service);
		
		dataHistService.createObjectDataHistory(policy, RangerDataHistService.ACTION_DELETE);
		
		bizUtil.createTrxLog(trxLogList);
		
		LOG.info("Policy Deleted Successfully. PolicyName : " + policyName);
	}

	@Override
	public RangerPolicy getPolicy(Long id) throws Exception {
		return policyService.read(id);
	}

	@Override
	public List<RangerPolicy> getPolicies(SearchFilter filter) throws Exception {
		if(LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.getPolicies()");
		}

		RangerPolicyList policyList = policyService.searchRangerPolicies(filter);

		predicateUtil.applyFilter(policyList.getPolicies(), filter);

		List<RangerPolicy> ret = policyList.getPolicies();

		if(LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDBStore.getPolicies()");
		}

		return ret;
	}


	public PList<RangerPolicy> getPaginatedPolicies(SearchFilter filter) throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.getPaginatedPolicies(+ " + filter + ")");
		}

		RangerPolicyList policyList = policyService.searchRangerPolicies(filter);

		if (LOG.isDebugEnabled()) {
			LOG.debug("before filter: count=" + policyList.getListSize());
		}
		predicateUtil.applyFilter(policyList.getPolicies(), filter);
		if (LOG.isDebugEnabled()) {
			LOG.debug("after filter: count=" + policyList.getListSize());
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDBStore.getPaginatedPolicies(" + filter + "): count=" + policyList.getListSize());
		}


		return new PList<RangerPolicy>(policyList.getPolicies(), policyList.getStartIndex(), policyList.getPageSize(), policyList.getTotalCount(),
				policyList.getResultSize(), policyList.getSortType(), policyList.getSortBy());

	}

	@Override
	public List<RangerPolicy> getServicePolicies(Long serviceId, SearchFilter filter) throws Exception {
		if(LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.getServicePolicies(" + serviceId + ")");
		}

		XXService service = daoMgr.getXXService().getById(serviceId);

		if (service == null) {
			throw new Exception("service does not exist - id='" + serviceId);
		}

		List<RangerPolicy> ret = getServicePolicies(service, filter);

		if(LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDBStore.getServicePolicies(" + serviceId + ") : policy-count=" + (ret == null ? 0 : ret.size()));
		}
		return ret;

	}

	public PList<RangerPolicy> getPaginatedServicePolicies(Long serviceId, SearchFilter filter) throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.getPaginatedServicePolicies(" + serviceId + ")");
		}

		XXService service = daoMgr.getXXService().getById(serviceId);

		if (service == null) {
			throw new Exception("service does not exist - id='" + serviceId);
		}

		PList<RangerPolicy> ret = getPaginatedServicePolicies(service.getName(), filter);

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDBStore.getPaginatedServicePolicies(" + serviceId + ")");
		}
		return ret;
	}

	@Override
	public List<RangerPolicy> getServicePolicies(String serviceName, SearchFilter filter) throws Exception {
		if(LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.getServicePolicies(" + serviceName + ")");
		}

		List<RangerPolicy> ret = null;

		XXService service = daoMgr.getXXService().findByName(serviceName);

		if (service == null) {
			throw new Exception("service does not exist - name='" + serviceName);
		}

		ret = getServicePolicies(service, filter);

		if(LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDBStore.getServicePolicies(" + serviceName + "): count=" + ((ret == null) ? 0 : ret.size()));
		}

		return ret;
	}

	private List<RangerPolicy> getServicePolicies(XXService service, SearchFilter filter) throws Exception {
		if(LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.getServicePolicies()");
		}

		if (service == null) {
			throw new Exception("service does not exist");
		}

		List<RangerPolicy> ret = null;

		ServicePolicies servicePolicies = RangerServicePoliciesCache.getInstance().getServicePolicies(service.getName(), this);
		List<RangerPolicy> policies = servicePolicies != null ? servicePolicies.getPolicies() : null;

		if(policies != null && filter != null) {
			ret = new ArrayList<RangerPolicy>(policies);
			predicateUtil.applyFilter(ret, filter);
		} else {
			ret = policies;
		}

		if(LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDBStore.getServicePolicies(): count=" + ((ret == null) ? 0 : ret.size()));
		}

		return ret;
	}

	private List<RangerPolicy> getServicePoliciesFromDb(XXService service) throws Exception {
		if(LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.getServicePoliciesFromDb(" + service.getName() + ")");
		}

		RangerPolicyRetriever policyRetriever = new RangerPolicyRetriever(daoMgr);

		List<RangerPolicy> ret = policyRetriever.getServicePolicies(service);

		if(LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDBStore.getServicePoliciesFromDb(" + service.getName() + "): count=" + ((ret == null) ? 0 : ret.size()));
		}

		return ret;
	}

	public PList<RangerPolicy> getPaginatedServicePolicies(String serviceName, SearchFilter filter) throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.getPaginatedServicePolicies(" + serviceName + ")");
		}

		if (filter == null) {
			filter = new SearchFilter();
		}

		filter.setParam(SearchFilter.SERVICE_NAME, serviceName);

		PList<RangerPolicy> ret = getPaginatedPolicies(filter);

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDBStore.getPaginatedServicePolicies(" + serviceName + "): count="
					+ ((ret == null) ? 0 : ret.getListSize()));
		}

		return ret;
	}

	@Override
	public ServicePolicies getServicePoliciesIfUpdated(String serviceName, Long lastKnownVersion) throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.getServicePoliciesIfUpdated(" + serviceName + ", " + lastKnownVersion + ")");
		}

		ServicePolicies ret = null;

		XXService serviceDbObj = daoMgr.getXXService().findByName(serviceName);

		if (serviceDbObj == null) {
			throw new Exception("service does not exist. name=" + serviceName);
		}

		if (lastKnownVersion == null || serviceDbObj.getPolicyVersion() == null || !lastKnownVersion.equals(serviceDbObj.getPolicyVersion())) {
			ret = RangerServicePoliciesCache.getInstance().getServicePolicies(serviceName, this);
		}

		if (ret != null && lastKnownVersion != null && lastKnownVersion.equals(ret.getPolicyVersion())) {
			// ServicePolicies are not changed
			ret = null;
		}

		if (LOG.isDebugEnabled()) {
			RangerServicePoliciesCache.getInstance().dump();
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDBStore.getServicePoliciesIfUpdated(" + serviceName + ", " + lastKnownVersion + "): count=" + ((ret == null || ret.getPolicies() == null) ? 0 : ret.getPolicies().size()));
		}

		return ret;
	}

	@Override
	public Long getServicePolicyVersion(String serviceName) {

		XXService serviceDbObj = daoMgr.getXXService().findByName(serviceName);

		return serviceDbObj != null ? serviceDbObj.getPolicyVersion() : null;
	}

	@Override
	public ServicePolicies getServicePolicies(String serviceName) throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.getServicePolicies(" + serviceName  + ")");
		}

		ServicePolicies ret = null;

		XXService serviceDbObj = daoMgr.getXXService().findByName(serviceName);

		if (serviceDbObj == null) {
			throw new Exception("service does not exist. name=" + serviceName);
		}

		RangerServiceDef serviceDef = getServiceDef(serviceDbObj.getType());

		if (serviceDef == null) {
			throw new Exception("service-def does not exist. id=" + serviceDbObj.getType());
		}
		List<RangerPolicy> policies = null;
		ServicePolicies.TagPolicies tagPolicies = null;

		if (serviceDbObj.getIsenabled()) {
			if (serviceDbObj.getTagService() != null) {
				XXService tagServiceDbObj = daoMgr.getXXService().getById(serviceDbObj.getTagService());

				if (tagServiceDbObj != null && tagServiceDbObj.getIsenabled()) {
					RangerServiceDef tagServiceDef = getServiceDef(tagServiceDbObj.getType());

					if (tagServiceDef == null) {
						throw new Exception("service-def does not exist. id=" + tagServiceDbObj.getType());
					}

					tagPolicies = new ServicePolicies.TagPolicies();

					tagPolicies.setServiceId(tagServiceDbObj.getId());
					tagPolicies.setServiceName(tagServiceDbObj.getName());
					tagPolicies.setPolicyVersion(tagServiceDbObj.getPolicyVersion());
					tagPolicies.setPolicyUpdateTime(tagServiceDbObj.getPolicyUpdateTime());
					tagPolicies.setPolicies(getServicePoliciesFromDb(tagServiceDbObj));
					tagPolicies.setServiceDef(tagServiceDef);
				}
			}

			policies = getServicePoliciesFromDb(serviceDbObj);

		} else {
			policies = new ArrayList<RangerPolicy>();
		}

		ret = new ServicePolicies();

		ret.setServiceId(serviceDbObj.getId());
		ret.setServiceName(serviceDbObj.getName());
		ret.setPolicyVersion(serviceDbObj.getPolicyVersion());
		ret.setPolicyUpdateTime(serviceDbObj.getPolicyUpdateTime());
		ret.setPolicies(policies);
		ret.setServiceDef(serviceDef);
		ret.setTagPolicies(tagPolicies);

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDBStore.getServicePolicies(" + serviceName  + "): count=" + ((ret == null || ret.getPolicies() == null) ? 0 : ret.getPolicies().size()));
		}

		return ret;
	}

	void createDefaultPolicies(XXService createdService, VXUser vXUser) throws Exception {
		RangerServiceDef serviceDef = getServiceDef(createdService.getType());

		if (serviceDef.getName().equals(EmbeddedServiceDefsUtil.EMBEDDED_SERVICEDEF_TAG_NAME)) {
			createDefaultTagPolicy(createdService);
		} else {
			// we need to create one policy for each resource hierarchy
			RangerServiceDefHelper serviceDefHelper = new RangerServiceDefHelper(serviceDef);
			int i = 1;
			for (List<RangerResourceDef> aHierarchy : serviceDefHelper.getResourceHierarchies()) {
				createDefaultPolicy(createdService, vXUser, aHierarchy, i);
				i++;
			}
		}
	}

	private void createDefaultTagPolicy(XXService createdService) throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.createDefaultTagPolicy() ");
		}

		String tagResourceDefName = null;
		boolean isConditionDefFound = false;

		RangerServiceDef tagServiceDef = getServiceDef(createdService.getType());
		List<RangerResourceDef> tagResourceDef = tagServiceDef.getResources();
		if (tagResourceDef != null && tagResourceDef.size() > 0) {
			// Assumption : First (and perhaps the only) resourceDef is the name of the tag resource
			RangerResourceDef theTagResourceDef = tagResourceDef.get(0);
			tagResourceDefName = theTagResourceDef.getName();
		} else {
			LOG.error("ServiceDBStore.createService() - Cannot create default TAG policy: Cannot get tagResourceDef Name.");
		}

		List<RangerPolicyConditionDef> policyConditionDefs = tagServiceDef.getPolicyConditions();

		if (CollectionUtils.isNotEmpty(policyConditionDefs)) {
			for (RangerPolicyConditionDef conditionDef : policyConditionDefs) {
				if (conditionDef.getName().equals(RANGER_TAG_EXPIRY_CONDITION_NAME)) {
					isConditionDefFound = true;
					break;
				}
			}
		}
		if (!isConditionDefFound) {
			LOG.error("ServiceDBStore.createService() - Cannot create default TAG policy: Cannot get tagPolicyConditionDef with name=" + RANGER_TAG_EXPIRY_CONDITION_NAME);
		}

		if (tagResourceDefName != null && isConditionDefFound) {

			String tagType = "EXPIRES_ON";

			String policyName = createdService.getName() + "-" + tagType;

			RangerPolicy policy = new RangerPolicy();

			policy.setIsEnabled(true);
			policy.setVersion(1L);
			policy.setName(policyName);
			policy.setService(createdService.getName());
			policy.setDescription(tagType + " Policy for TAG Service: " + createdService.getName());
			policy.setIsAuditEnabled(true);

			Map<String, RangerPolicyResource> resourceMap = new HashMap<String, RangerPolicyResource>();

			RangerPolicyResource polRes = new RangerPolicyResource();
			polRes.setIsExcludes(false);
			polRes.setIsRecursive(false);
			polRes.setValue(tagType);
			resourceMap.put(tagResourceDefName, polRes);

			policy.setResources(resourceMap);

			List<RangerPolicyItem> policyItems = new ArrayList<RangerPolicyItem>();

			RangerPolicyItem policyItem = new RangerPolicyItem();

			List<String> groups = new ArrayList<String>();
			groups.add(RangerConstants.GROUP_PUBLIC);
			policyItem.setGroups(groups);

			List<XXAccessTypeDef> accessTypeDefs = daoMgr.getXXAccessTypeDef().findByServiceDefId(createdService.getType());
			List<RangerPolicyItemAccess> accesses = new ArrayList<RangerPolicyItemAccess>();
			for (XXAccessTypeDef accessTypeDef : accessTypeDefs) {
				RangerPolicyItemAccess access = new RangerPolicyItemAccess();
				access.setType(accessTypeDef.getName());
				access.setIsAllowed(true);
				accesses.add(access);
			}
			policyItem.setAccesses(accesses);

			List<RangerPolicyItemCondition> policyItemConditions = new ArrayList<RangerPolicyItemCondition>();
			List<String> values = new ArrayList<String>();
			values.add("yes");
			RangerPolicyItemCondition policyItemCondition = new RangerPolicyItemCondition(RANGER_TAG_EXPIRY_CONDITION_NAME, values);
			policyItemConditions.add(policyItemCondition);

			policyItem.setConditions(policyItemConditions);
			policyItem.setDelegateAdmin(Boolean.FALSE);

			policyItems.add(policyItem);

			policy.setDenyPolicyItems(policyItems);

			policy = createPolicy(policy);
		} else {
			LOG.error("ServiceDBStore.createService() - Cannot create default TAG policy, tagResourceDefName=" + tagResourceDefName +
					", tagPolicyConditionName=" + RANGER_TAG_EXPIRY_CONDITION_NAME);
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== ServiceDBStore.createDefaultTagPolicy()");
		}
	}

	private void createDefaultPolicy(XXService createdService, VXUser vXUser, List<RangerResourceDef> resourceHierarchy, int num) throws Exception {
		RangerPolicy policy = new RangerPolicy();
		String policyName=createdService.getName()+"-"+num+"-"+DateUtil.dateToString(DateUtil.getUTCDate(),"yyyyMMddHHmmss");
		
		policy.setIsEnabled(true);
		policy.setVersion(1L);
		policy.setName(policyName);
		policy.setService(createdService.getName());
		policy.setDescription("Default Policy for Service: " + createdService.getName());
		policy.setIsAuditEnabled(true);
		
		policy.setResources(createDefaultPolicyResource(resourceHierarchy));
		
		if (vXUser != null) {
			List<RangerPolicyItem> policyItems = new ArrayList<RangerPolicyItem>();
			RangerPolicyItem policyItem = new RangerPolicyItem();

			List<String> users = new ArrayList<String>();
			users.add(vXUser.getName());
			policyItem.setUsers(users);

			List<XXAccessTypeDef> accessTypeDefs = daoMgr.getXXAccessTypeDef().findByServiceDefId(createdService.getType());
			List<RangerPolicyItemAccess> accesses = new ArrayList<RangerPolicyItemAccess>();
			for(XXAccessTypeDef accessTypeDef : accessTypeDefs) {
				RangerPolicyItemAccess access = new RangerPolicyItemAccess();
				access.setType(accessTypeDef.getName());
				access.setIsAllowed(true);
				accesses.add(access);
			}
			policyItem.setAccesses(accesses);

			policyItem.setDelegateAdmin(true);
			policyItems.add(policyItem);
			policy.setPolicyItems(policyItems);
		}
		policy = createPolicy(policy);
	}

	Map<String, RangerPolicyResource> createDefaultPolicyResource(List<RangerResourceDef> resourceHierarchy) throws Exception {
		Map<String, RangerPolicyResource> resourceMap = new HashMap<>();

		for (RangerResourceDef resourceDef : resourceHierarchy) {
			RangerPolicyResource polRes = new RangerPolicyResource();
			polRes.setIsExcludes(false);
			polRes.setIsRecursive(false);

			String value = "*";
			if("path".equalsIgnoreCase(resourceDef.getName())) {
				value = "/*";
			}

			if(resourceDef.getRecursiveSupported()) {
				polRes.setIsRecursive(Boolean.TRUE);
			}

			polRes.setValue(value);
			resourceMap.put(resourceDef.getName(), polRes);
		}
		return resourceMap;
	}

	private Map<String, String> validateRequiredConfigParams(RangerService service, Map<String, String> configs) {
		if(LOG.isDebugEnabled()) {
			LOG.debug("==> ServiceDBStore.validateRequiredConfigParams()");
		}
		if(configs == null) {
			return null;
		}
		
		List<XXServiceConfigDef> svcConfDefList = daoMgr.getXXServiceConfigDef()
				.findByServiceDefName(service.getType());
		for(XXServiceConfigDef svcConfDef : svcConfDefList ) {
			String confField = configs.get(svcConfDef.getName());
			
			if(svcConfDef.getIsMandatory() && stringUtil.isEmpty(confField)) {
				throw restErrorUtil.createRESTException(
						"Please provide value of mandatory: "+ svcConfDef.getName(),
						MessageEnums.INVALID_INPUT_DATA);
			}
		}
		Map<String, String> validConfigs = new HashMap<String, String>();
		for(Entry<String, String> config : configs.entrySet()) {
			if(!stringUtil.isEmpty(config.getValue())) {
				validConfigs.put(config.getKey(), config.getValue());
			}
		}
		return validConfigs;
	}

	private void handlePolicyUpdate(RangerService service) throws Exception {
		updatePolicyVersion(service);
	}

	private void updatePolicyVersion(RangerService service) throws Exception {
		if(service == null || service.getId() == null) {
			return;
		}

		XXServiceDao serviceDao = daoMgr.getXXService();

		XXService serviceDbObj = serviceDao.getById(service.getId());

		if(serviceDbObj == null) {
			LOG.warn("updatePolicyVersion(serviceId=" + service.getId() + "): service not found");

			return;
		}

		service.setPolicyVersion(getNextVersion(service.getPolicyVersion()));
		service.setPolicyUpdateTime(new Date());

		serviceDbObj.setPolicyVersion(service.getPolicyVersion());
		serviceDbObj.setPolicyUpdateTime(service.getPolicyUpdateTime());

		serviceDao.update(serviceDbObj);

		// if this is a tag service, update all services that refer to this tag service
		// so that next policy-download from plugins will get updated tag policies
		boolean isTagService = serviceDbObj.getType() == EmbeddedServiceDefsUtil.instance().getTagServiceDefId();
		if(isTagService) {
			List<XXService> referringServices = serviceDao.findByTagServiceId(serviceDbObj.getId());

			if(CollectionUtils.isNotEmpty(referringServices)) {
				for(XXService referringService : referringServices) {
					referringService.setPolicyVersion(getNextVersion(referringService.getPolicyVersion()));
					referringService.setPolicyUpdateTime(service.getPolicyUpdateTime());

					serviceDao.update(referringService);
				}
			}
		}
	}

	private XXPolicyItem createNewPolicyItemForPolicy(RangerPolicy policy, XXPolicy xPolicy, RangerPolicy.RangerPolicyItem policyItem, XXServiceDef xServiceDef, int itemOrder, int policyItemType) throws Exception {
		XXPolicyItem xPolicyItem = new XXPolicyItem();

		xPolicyItem = (XXPolicyItem) rangerAuditFields.populateAuditFields(xPolicyItem, xPolicy);

		xPolicyItem.setDelegateAdmin(policyItem.getDelegateAdmin());
		xPolicyItem.setItemType(policyItemType);
		xPolicyItem.setIsEnabled(Boolean.TRUE);
		xPolicyItem.setComments(null);
		xPolicyItem.setPolicyId(policy.getId());
		xPolicyItem.setOrder(itemOrder);
		xPolicyItem = daoMgr.getXXPolicyItem().create(xPolicyItem);

		List<RangerPolicyItemAccess> accesses = policyItem.getAccesses();
		for (int i = 0; i < accesses.size(); i++) {
			RangerPolicyItemAccess access = accesses.get(i);

			XXAccessTypeDef xAccTypeDef = daoMgr.getXXAccessTypeDef()
					.findByNameAndServiceId(access.getType(),
							xPolicy.getService());
			if (xAccTypeDef == null) {
				throw new Exception(access.getType() + ": is not a valid access-type. policy='" + policy.getName() + "' service='" + policy.getService() + "'");
			}

			XXPolicyItemAccess xPolItemAcc = new XXPolicyItemAccess();

			xPolItemAcc = (XXPolicyItemAccess) rangerAuditFields.populateAuditFields(xPolItemAcc, xPolicyItem);
			xPolItemAcc.setIsAllowed(access.getIsAllowed());
			xPolItemAcc.setType(xAccTypeDef.getId());
			xPolItemAcc.setPolicyitemid(xPolicyItem.getId());
			xPolItemAcc.setOrder(i);

			daoMgr.getXXPolicyItemAccess().create(xPolItemAcc);
		}

		List<String> users = policyItem.getUsers();
		for(int i = 0; i < users.size(); i++) {
			String user = users.get(i);

			XXUser xUser = daoMgr.getXXUser().findByUserName(user);
			if(xUser == null) {
				throw new Exception(user + ": user does not exist. policy='"+  policy.getName() + "' service='"+ policy.getService() + "'");
			}
			XXPolicyItemUserPerm xUserPerm = new XXPolicyItemUserPerm();
			xUserPerm = (XXPolicyItemUserPerm) rangerAuditFields.populateAuditFields(xUserPerm, xPolicyItem);
			xUserPerm.setUserId(xUser.getId());
			xUserPerm.setPolicyItemId(xPolicyItem.getId());
			xUserPerm.setOrder(i);
			xUserPerm = daoMgr.getXXPolicyItemUserPerm().create(xUserPerm);
		}

		List<String> groups = policyItem.getGroups();
		for(int i = 0; i < groups.size(); i++) {
			String group = groups.get(i);

			XXGroup xGrp = daoMgr.getXXGroup().findByGroupName(group);
			if(xGrp == null) {
				throw new Exception(group + ": group does not exist. policy='"+  policy.getName() + "' service='"+ policy.getService() + "'");
			}
			XXPolicyItemGroupPerm xGrpPerm = new XXPolicyItemGroupPerm();
			xGrpPerm = (XXPolicyItemGroupPerm) rangerAuditFields.populateAuditFields(xGrpPerm, xPolicyItem);
			xGrpPerm.setGroupId(xGrp.getId());
			xGrpPerm.setPolicyItemId(xPolicyItem.getId());
			xGrpPerm.setOrder(i);
			xGrpPerm = daoMgr.getXXPolicyItemGroupPerm().create(xGrpPerm);
		}

		List<RangerPolicyItemCondition> conditions = policyItem.getConditions();
		for(RangerPolicyItemCondition condition : conditions) {
			XXPolicyConditionDef xPolCond = daoMgr
					.getXXPolicyConditionDef().findByServiceDefIdAndName(
							xServiceDef.getId(), condition.getType());

			if(xPolCond == null) {
				throw new Exception(condition.getType() + ": is not a valid condition-type. policy='"+  xPolicy.getName() + "' service='"+ xPolicy.getService() + "'");
			}

			for(int i = 0; i < condition.getValues().size(); i++) {
				String value = condition.getValues().get(i);
				XXPolicyItemCondition xPolItemCond = new XXPolicyItemCondition();
				xPolItemCond = (XXPolicyItemCondition) rangerAuditFields.populateAuditFields(xPolItemCond, xPolicyItem);
				xPolItemCond.setPolicyItemId(xPolicyItem.getId());
				xPolItemCond.setType(xPolCond.getId());
				xPolItemCond.setValue(value);
				xPolItemCond.setOrder(i);

				daoMgr.getXXPolicyItemCondition().create(xPolItemCond);
			}
		}

		return xPolicyItem;
	}

	private void createNewPolicyItemsForPolicy(RangerPolicy policy, XXPolicy xPolicy, List<RangerPolicyItem> policyItems, XXServiceDef xServiceDef, int policyItemType) throws Exception {
		if(CollectionUtils.isNotEmpty(policyItems)) {
			for (int itemOrder = 0; itemOrder < policyItems.size(); itemOrder++) {
				RangerPolicyItem policyItem = policyItems.get(itemOrder);
				XXPolicyItem xPolicyItem = createNewPolicyItemForPolicy(policy, xPolicy, policyItem, xServiceDef, itemOrder, policyItemType);
			}
		}
	}

	private void createNewDataMaskPolicyItemsForPolicy(RangerPolicy policy, XXPolicy xPolicy, List<RangerDataMaskPolicyItem> policyItems, XXServiceDef xServiceDef, int policyItemType) throws Exception {
		if(CollectionUtils.isNotEmpty(policyItems)) {
			for (int itemOrder = 0; itemOrder < policyItems.size(); itemOrder++) {
				RangerDataMaskPolicyItem policyItem = policyItems.get(itemOrder);

				XXPolicyItem xPolicyItem = createNewPolicyItemForPolicy(policy, xPolicy, policyItem, xServiceDef, itemOrder, policyItemType);

				RangerPolicy.RangerPolicyItemDataMaskInfo dataMaskInfo = policyItem.getDataMaskInfo();

				if(dataMaskInfo != null) {
					XXDataMaskTypeDef dataMaskDef = daoMgr.getXXDataMaskTypeDef().findByNameAndServiceId(dataMaskInfo.getDataMaskType(), xPolicy.getService());

					if(dataMaskDef == null) {
						throw new Exception(dataMaskInfo.getDataMaskType() + ": is not a valid datamask-type. policy='" + policy.getName() + "' service='" + policy.getService() + "'");
					}

					XXPolicyItemDataMaskInfo xxDataMaskInfo = new XXPolicyItemDataMaskInfo();

					xxDataMaskInfo.setPolicyitemid(xPolicyItem.getId());
					xxDataMaskInfo.setType(dataMaskDef.getId());
					xxDataMaskInfo.setConditionExpr(dataMaskInfo.getConditionExpr());
					xxDataMaskInfo.setValueExpr(dataMaskInfo.getValueExpr());

					xxDataMaskInfo = daoMgr.getXXPolicyItemDataMaskInfo().create(xxDataMaskInfo);
				}
			}
		}
	}

	private void createNewResourcesForPolicy(RangerPolicy policy, XXPolicy xPolicy, Map<String, RangerPolicyResource> resources) throws Exception {
		
		for (Entry<String, RangerPolicyResource> resource : resources.entrySet()) {
			RangerPolicyResource policyRes = resource.getValue();

			XXResourceDef xResDef = daoMgr.getXXResourceDef()
					.findByNameAndPolicyId(resource.getKey(), policy.getId());
			if (xResDef == null) {
				throw new Exception(resource.getKey() + ": is not a valid resource-type. policy='"+  policy.getName() + "' service='"+ policy.getService() + "'");
			}

			XXPolicyResource xPolRes = new XXPolicyResource();
			xPolRes = (XXPolicyResource) rangerAuditFields.populateAuditFields(xPolRes, xPolicy);

			xPolRes.setIsExcludes(policyRes.getIsExcludes());
			xPolRes.setIsRecursive(policyRes.getIsRecursive());
			xPolRes.setPolicyId(policy.getId());
			xPolRes.setResDefId(xResDef.getId());
			xPolRes = daoMgr.getXXPolicyResource().create(xPolRes);

			List<String> values = policyRes.getValues();
			if(CollectionUtils.isNotEmpty(values)){
				for(int i = 0; i < values.size(); i++) {
					if(values.get(i)!=null){
						XXPolicyResourceMap xPolResMap = new XXPolicyResourceMap();
						xPolResMap = (XXPolicyResourceMap) rangerAuditFields.populateAuditFields(xPolResMap, xPolRes);
						xPolResMap.setResourceId(xPolRes.getId());
						xPolResMap.setValue(values.get(i));
						xPolResMap.setOrder(i);
						xPolResMap = daoMgr.getXXPolicyResourceMap().create(xPolResMap);
					}
				}
			}
		}
	}

	private Boolean deleteExistingPolicyItems(RangerPolicy policy) {
		if(policy == null) {
			return false;
		}
		
		XXPolicyItemDao policyItemDao = daoMgr.getXXPolicyItem();
		List<XXPolicyItem> policyItems = policyItemDao.findByPolicyId(policy.getId());
		for(XXPolicyItem policyItem : policyItems) {
			Long polItemId = policyItem.getId();
			
			XXPolicyItemConditionDao polCondDao = daoMgr.getXXPolicyItemCondition();
			List<XXPolicyItemCondition> conditions = polCondDao.findByPolicyItemId(polItemId);
			for(XXPolicyItemCondition condition : conditions) {
				polCondDao.remove(condition);
			}
			
			XXPolicyItemGroupPermDao grpPermDao = daoMgr.getXXPolicyItemGroupPerm();
			List<XXPolicyItemGroupPerm> groups = grpPermDao.findByPolicyItemId(polItemId);
			for(XXPolicyItemGroupPerm group : groups) {
				grpPermDao.remove(group);
			}
			
			XXPolicyItemUserPermDao userPermDao = daoMgr.getXXPolicyItemUserPerm();
			List<XXPolicyItemUserPerm> users = userPermDao.findByPolicyItemId(polItemId);
			for(XXPolicyItemUserPerm user : users) {
				userPermDao.remove(user);
			}
			
			XXPolicyItemAccessDao polItemAccDao = daoMgr.getXXPolicyItemAccess();
			List<XXPolicyItemAccess> accesses = polItemAccDao.findByPolicyItemId(polItemId);
			for(XXPolicyItemAccess access : accesses) {
				polItemAccDao.remove(access);
			}

			XXPolicyItemDataMaskInfoDao polItemDataMaskInfoDao = daoMgr.getXXPolicyItemDataMaskInfo();
			List<XXPolicyItemDataMaskInfo> dataMaskInfos = polItemDataMaskInfoDao.findByPolicyItemId(polItemId);
			for(XXPolicyItemDataMaskInfo dataMaskInfo : dataMaskInfos) {
				polItemDataMaskInfoDao.remove(dataMaskInfo);
			}

			policyItemDao.remove(policyItem);
		}
		return true;
	}

	private Boolean deleteExistingPolicyResources(RangerPolicy policy) {
		if(policy == null) {
			return false;
		}
		
		List<XXPolicyResource> resources = daoMgr.getXXPolicyResource().findByPolicyId(policy.getId());
		
		XXPolicyResourceDao resDao = daoMgr.getXXPolicyResource();
		for(XXPolicyResource resource : resources) {
			List<XXPolicyResourceMap> resMapList = daoMgr.getXXPolicyResourceMap().findByPolicyResId(resource.getId());
			
			XXPolicyResourceMapDao resMapDao = daoMgr.getXXPolicyResourceMap();
			for(XXPolicyResourceMap resMap : resMapList) {
				resMapDao.remove(resMap);
			}
			resDao.remove(resource);
		}
		return true;
	}

	@Override
	public Boolean getPopulateExistingBaseFields() {
		return populateExistingBaseFields;
	}

	@Override
	public void setPopulateExistingBaseFields(Boolean populateExistingBaseFields) {
		this.populateExistingBaseFields = populateExistingBaseFields;
	}

	public RangerPolicy getPolicyFromEventTime(String eventTime, Long policyId) {

		XXDataHist xDataHist = daoMgr.getXXDataHist().findObjByEventTimeClassTypeAndId(eventTime,
				AppConstants.CLASS_TYPE_RANGER_POLICY, policyId);

		if (xDataHist == null) {
			String errMsg = "No policy history found for given time: " + eventTime;
			LOG.error(errMsg);
			throw restErrorUtil.createRESTException(errMsg, MessageEnums.DATA_NOT_FOUND);
		}

		String content = xDataHist.getContent();
		RangerPolicy policy = (RangerPolicy) dataHistService.writeJsonToJavaObject(content, RangerPolicy.class);

		return policy;
	}

	public VXString getPolicyVersionList(Long policyId) {
		List<Integer> versionList = daoMgr.getXXDataHist().getVersionListOfObject(policyId,
				AppConstants.CLASS_TYPE_RANGER_POLICY);

		VXString vXString = new VXString();
		vXString.setValue(StringUtils.join(versionList, ","));

		return vXString;
	}

	public RangerPolicy getPolicyForVersionNumber(Long policyId, int versionNo) {
		XXDataHist xDataHist = daoMgr.getXXDataHist().findObjectByVersionNumber(policyId,
				AppConstants.CLASS_TYPE_RANGER_POLICY, versionNo);

		if (xDataHist == null) {
			throw restErrorUtil.createRESTException("No Policy found for given version.", MessageEnums.DATA_NOT_FOUND);
		}

		String content = xDataHist.getContent();
		RangerPolicy policy = (RangerPolicy) dataHistService.writeJsonToJavaObject(content, RangerPolicy.class);

		return policy;
	}

	void updatePolicySignature(RangerPolicy policy) {
		RangerPolicyResourceSignature policySignature = factory.createPolicyResourceSignature(policy);
		String signature = policySignature.getSignature();
		policy.setResourceSignature(signature);
		if (LOG.isDebugEnabled()) {
			String message = String.format("Setting signature on policy id=%d, name=%s to [%s]", policy.getId(), policy.getName(), signature);
			LOG.debug(message);
		}
	}

	// when a service-def is updated, the updated service-def should be made available to plugins
	//   this is achieved by incrementing policyVersion of all services of this service-def
	protected void updateServicesForServiceDefUpdate(RangerServiceDef serviceDef) throws Exception {
		if(serviceDef == null) {
			return;
		}

		boolean isTagServiceDef = StringUtils.equals(serviceDef.getName(), EmbeddedServiceDefsUtil.EMBEDDED_SERVICEDEF_TAG_NAME);

		XXServiceDao serviceDao = daoMgr.getXXService();

		List<XXService> services = serviceDao.findByServiceDefId(serviceDef.getId());

		if(CollectionUtils.isNotEmpty(services)) {
			for(XXService service : services) {
				service.setPolicyVersion(getNextVersion(service.getPolicyVersion()));
				service.setPolicyUpdateTime(serviceDef.getUpdateTime());

				serviceDao.update(service);

				if(isTagServiceDef) {
					List<XXService> referrringServices = serviceDao.findByTagServiceId(service.getId());

					if(CollectionUtils.isNotEmpty(referrringServices)) {
						for(XXService referringService : referrringServices) {
							referringService.setPolicyVersion(getNextVersion(referringService.getPolicyVersion()));
							referringService.setPolicyUpdateTime(serviceDef.getUpdateTime());

							serviceDao.update(referringService);
						}
					}
				}
			}
		}
	}


	private String getServiceName(Long serviceId) {
		String ret = null;

		if(serviceId != null) {
			XXService service = daoMgr.getXXService().getById(serviceId);

			if(service != null) {
				ret = service.getName();
			}
		}

		return ret;
	}

}
