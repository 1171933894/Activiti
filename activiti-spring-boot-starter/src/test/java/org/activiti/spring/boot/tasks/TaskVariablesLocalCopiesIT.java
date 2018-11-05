package org.activiti.spring.boot.tasks;

import org.activiti.api.process.model.ProcessDefinition;
import org.activiti.api.process.model.ProcessInstance;
import org.activiti.api.process.model.builders.ProcessPayloadBuilder;
import org.activiti.api.process.runtime.ProcessRuntime;
import org.activiti.api.process.runtime.conf.ProcessRuntimeConfiguration;
import org.activiti.api.runtime.shared.query.Page;
import org.activiti.api.runtime.shared.query.Pageable;
import org.activiti.api.task.model.Task;
import org.activiti.api.task.model.builders.TaskPayloadBuilder;
import org.activiti.api.task.runtime.TaskRuntime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration
public class TaskVariablesLocalCopiesIT {

    private static final String TWOTASK_PROCESS = "twoTaskProcess";

    @Autowired
    private ProcessRuntime processRuntime;

    @Autowired
    private TaskRuntime taskRuntime;

    @Autowired
    private UserDetailsService userDetailsService;

    @Before
    public void init() {


    }

    @Test
    @WithUserDetails(value = "salaboy", userDetailsServiceBeanName = "myUserDetailsService")
    public void shouldGetConfiguration() {
        //when
        ProcessRuntimeConfiguration configuration = processRuntime.configuration();

        //then
        assertThat(configuration).isNotNull();
    }

    @Test
    @WithUserDetails(value = "salaboy", userDetailsServiceBeanName = "myUserDetailsService")
    public void shouldGetAvailableProcessDefinitionForTheGivenUser() {
        //when
        Page<ProcessDefinition> processDefinitionPage = processRuntime.processDefinitions(Pageable.of(0,
                                                                                                      50));
        //then
        assertThat(processDefinitionPage.getContent()).isNotNull();
        assertThat(processDefinitionPage.getContent())
                .extracting(ProcessDefinition::getKey)
                .contains(TWOTASK_PROCESS);
    }

    @Test
    @WithUserDetails(value = "salaboy", userDetailsServiceBeanName = "myUserDetailsService")
    public void processInstanceVariablesCopiedIntoTasksByDefault() {

        Map<String,Object> startVariables = new HashMap<>();
        startVariables.put("start1","start1");
        startVariables.put("start2","start2");

        //when
        ProcessInstance twoTaskInstance = processRuntime.start(ProcessPayloadBuilder.start()
                .withProcessDefinitionKey(TWOTASK_PROCESS)
                .withVariables(startVariables)
                .build());

        assertThat(processRuntime.variables(ProcessPayloadBuilder.variables().withProcessInstance(twoTaskInstance).build()))
                .extracting("name", "value")
                .containsExactly(
                        tuple("start1", "start1"),
                        tuple("start2", "start2"));


        //both tasks should have the process variables
        Task task1 = taskRuntime.tasks(Pageable.of(0, 10),TaskPayloadBuilder.tasks().withTaskDefinitionKey("usertask1").build()).getContent().get(0);
        assertThat(taskRuntime.variables(TaskPayloadBuilder.variables().withTaskId(task1.getId()).build()))
                .extracting("name", "value")
                .containsExactly(
                        tuple("start1", "start1"),
                        tuple("start2", "start2"));

        Task task2 = taskRuntime.tasks(Pageable.of(0, 10),TaskPayloadBuilder.tasks().withTaskDefinitionKey("usertask2").build()).getContent().get(0);
        assertThat(taskRuntime.variables(TaskPayloadBuilder.variables().withTaskId(task2.getId()).build()))
                .extracting("name", "value")
                .containsExactly(
                        tuple("start1", "start1"),
                        tuple("start2", "start2"));


        //if one modifies, the other should not see the modification
        taskRuntime.setVariables(TaskPayloadBuilder.setVariables().withTaskId(task1.getId()).withVariables(Collections.singletonMap("start1","modifiedstart1")).build());

        //the task where it was modified should reflect the modification
        assertThat(taskRuntime.variables(TaskPayloadBuilder.variables().withTaskId(task1.getId()).build()))
                .extracting("name", "value")
                .containsExactly(
                        tuple("start1", "modifiedstart1"),
                        tuple("start2", "start2"));

        //other does not see
        assertThat(taskRuntime.variables(TaskPayloadBuilder.variables().withTaskId(task2.getId()).build()))
                .extracting("name", "value")
                .containsExactly(
                        tuple("start1", "start1"),
                        tuple("start2", "start2"));

        //complete and change var again
        taskRuntime.claim(TaskPayloadBuilder.claim().withTaskId(task1.getId()).build());
        taskRuntime.complete(TaskPayloadBuilder.complete().withTaskId(task1.getId()).withVariable("start1","modagainstart1").build());

        //after completion the process variable should be updated but only the one that was modified
        assertThat(processRuntime.variables(ProcessPayloadBuilder.variables().withProcessInstance(twoTaskInstance).build()))
                .extracting("name", "value")
                .containsExactly(
                        tuple("start1", "modagainstart1"),
                        tuple("start2", "start2"));

        //and task2 should not see the change
        assertThat(taskRuntime.variables(TaskPayloadBuilder.variables().withTaskId(task2.getId()).build()))
                .extracting("name", "value")
                .containsExactly(
                        tuple("start1", "start1"),
                        tuple("start2", "start2"));

    }


}