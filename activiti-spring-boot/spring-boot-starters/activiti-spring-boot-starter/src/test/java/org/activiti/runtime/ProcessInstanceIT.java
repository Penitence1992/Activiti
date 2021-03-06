/*
 * Copyright 2017 Alfresco, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.activiti.runtime;

import org.activiti.keycloak.KeycloakEnabledBaseTestIT;
import org.activiti.keycloak.ProcessInstanceKeycloakRestTemplate;
import org.activiti.services.core.model.ProcessDefinition;
import org.activiti.services.core.model.ProcessInstance;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.PagedResources;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;

import static org.activiti.keycloak.ProcessInstanceKeycloakRestTemplate.PROCESS_INSTANCES_RELATIVE_URL;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource("classpath:application-test.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ProcessInstanceIT extends KeycloakEnabledBaseTestIT {

    private static final String SIMPLE_PROCESS = "SimpleProcess";
    public static final String PROCESS_DEFINITIONS_URL = "/v1/process-definitions/";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProcessInstanceKeycloakRestTemplate processInstanceRestTemplate;


    private Map<String, String> processDefinitionIds = new HashMap<>();
    @Before
    public void setUp() throws Exception{
        super.setUp();
        ResponseEntity<PagedResources<ProcessDefinition>> processDefinitions = getProcessDefinitions();
        assertThat(processDefinitions.getStatusCode()).isEqualTo(HttpStatus.OK);


        assertThat(processDefinitions.getBody().getContent()).hasSize(4);
        for (ProcessDefinition pd : processDefinitions.getBody().getContent()) {
            processDefinitionIds.put(pd.getName(), pd.getId());
        }
    }


    @Test
    public void shouldStartProcess() throws Exception {
        //when
        ResponseEntity<ProcessInstance> entity = processInstanceRestTemplate.startProcess(processDefinitionIds.get(SIMPLE_PROCESS), accessToken);

        //then
        assertThat(entity).isNotNull();
        ProcessInstance returnedProcInst = entity.getBody();
        assertThat(returnedProcInst).isNotNull();
        assertThat(returnedProcInst.getId()).isNotNull();
        assertThat(returnedProcInst.getProcessDefinitionId()).contains("SimpleProcess:");
        assertThat(returnedProcInst.getInitiator()).isNotNull();
        assertThat(returnedProcInst.getInitiator()).isEqualTo(keycloaktestuser);//will only match if using username not id
    }

    @Test
    public void shouldRetrieveProcessInstanceById() throws Exception {


        //given
        ResponseEntity<ProcessInstance> startedProcessEntity = processInstanceRestTemplate.startProcess(processDefinitionIds.get(SIMPLE_PROCESS),accessToken);

        //when

        ResponseEntity<ProcessInstance> retrievedEntity = restTemplate.exchange(
                PROCESS_INSTANCES_RELATIVE_URL + startedProcessEntity.getBody().getId(),
                HttpMethod.GET,
                getRequestEntityWithHeaders(),
                new ParameterizedTypeReference<ProcessInstance>() {
                });

        //then
        assertThat(retrievedEntity.getBody()).isNotNull();
        assertThat(retrievedEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retrievedEntity.getBody().getId()).isNotNull();
    }

    @Test
    public void shouldRetrieveListOfProcessInstances() throws Exception {

        //given
        processInstanceRestTemplate.startProcess(processDefinitionIds.get(SIMPLE_PROCESS),accessToken);
        processInstanceRestTemplate.startProcess(processDefinitionIds.get(SIMPLE_PROCESS),accessToken);
        processInstanceRestTemplate.startProcess(processDefinitionIds.get(SIMPLE_PROCESS),accessToken);

        //when
        ResponseEntity<PagedResources<ProcessInstance>> processInstancesPage = restTemplate.exchange(PROCESS_INSTANCES_RELATIVE_URL + "?page=0&size=2",
                                                                                                          HttpMethod.GET,
                                                                                                            getRequestEntityWithHeaders(),
                                                                                                          new ParameterizedTypeReference<PagedResources<ProcessInstance>>() {
                                                                                                          });

        //then
        assertThat(processInstancesPage).isNotNull();
        assertThat(processInstancesPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(processInstancesPage.getBody().getContent()).hasSize(2);
        assertThat(processInstancesPage.getBody().getMetadata().getTotalPages()).isGreaterThanOrEqualTo(2);
    }


    @Test
    public void suspendShouldPutProcessInstanceInSuspendedState() throws Exception {
        //given
        ResponseEntity<ProcessInstance> startProcessEntity = processInstanceRestTemplate.startProcess(processDefinitionIds.get(SIMPLE_PROCESS),accessToken);

        //when
        ResponseEntity<Void> responseEntity = executeRequestSuspendProcess(startProcessEntity);

        //then
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<ProcessInstance> processInstanceEntity = processInstanceRestTemplate.getProcessInstance(startProcessEntity,accessToken);
        assertThat(processInstanceEntity.getBody().getStatus()).isEqualTo(ProcessInstance.ProcessInstanceStatus.SUSPENDED.name());
    }

    private ResponseEntity<Void> executeRequestSuspendProcess(ResponseEntity<ProcessInstance> processInstanceEntity) {
        ResponseEntity<Void> responseEntity = restTemplate.exchange(PROCESS_INSTANCES_RELATIVE_URL + processInstanceEntity.getBody().getId() + "/suspend",
                HttpMethod.POST,
                getRequestEntityWithHeaders(),
                new ParameterizedTypeReference<Void>() {
                });
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        return responseEntity;
    }

    @Test
    public void activateShouldPutASuspendedProcessInstanceBackToActiveState() throws Exception {
        //given
        ResponseEntity<ProcessInstance> startProcessEntity = processInstanceRestTemplate.startProcess(processDefinitionIds.get(SIMPLE_PROCESS),accessToken);
        executeRequestSuspendProcess(startProcessEntity);

        //when
        ResponseEntity<Void> responseEntity = restTemplate.exchange(PROCESS_INSTANCES_RELATIVE_URL + startProcessEntity.getBody().getId() + "/activate",
                HttpMethod.POST,
                getRequestEntityWithHeaders(),
                new ParameterizedTypeReference<Void>() {
                });

        //then
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<ProcessInstance> processInstanceEntity = processInstanceRestTemplate.getProcessInstance(startProcessEntity,accessToken);
        assertThat(processInstanceEntity.getBody().getStatus()).isEqualTo(ProcessInstance.ProcessInstanceStatus.RUNNING.name());
    }


    private ResponseEntity<PagedResources<ProcessDefinition>> getProcessDefinitions() {
        ParameterizedTypeReference<PagedResources<ProcessDefinition>> responseType = new ParameterizedTypeReference<PagedResources<ProcessDefinition>>() {
        };

        return restTemplate.exchange(PROCESS_DEFINITIONS_URL,
                HttpMethod.GET,
                getRequestEntityWithHeaders(),
                responseType);
    }
}