package ${package};

import java.text.ParseException;
import java.text.SimpleDateFormat;

import com.google.common.collect.Maps;
import org.activiti.engine.*;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.form.TaskFormData;
import org.activiti.engine.impl.form.DateFormType;
import org.activiti.engine.impl.form.StringFormType;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.DeploymentBuilder;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * @Description: 启动类
 * @Author: heyuanxin3
 * @Date: 2019/10/27 15:03
 */
public class HelloDemo {

    private static final Logger LOGGER = LoggerFactory.getLogger(HelloDemo.class);

    public static void main(String[] args) throws ParseException {
        LOGGER.error("启动程序");

        // 创建流程引擎
        ProcessEngine processEngine = getProcessEngine();

        // 部署流程定义文件
        ProcessDefinition processDefinition = getProcessDefinition(processEngine);

        // 启动流程任务
        ProcessInstance processInstance = getProcessEngine(processEngine, processDefinition);

        // 处理流程任务
        dealTask(processEngine, processInstance);

        LOGGER.error("结束程序");
    }

    private static void dealTask(ProcessEngine processEngine, ProcessInstance processInstance) throws ParseException {
        Scanner scanner = new Scanner(System.in);
        while (processInstance != null && !processInstance.isEnded()) {
            TaskService taskService = processEngine.getTaskService();
            List<Task> list = taskService.createTaskQuery().list();
            LOGGER.error("待处理的任务数:{}", list.size());
            for (Task task : list) {
                LOGGER.error("正在处理的任务:{}", task.getName());
                FormService formService = processEngine.getFormService();
                TaskFormData taskFormData = formService.getTaskFormData(task.getId());
                List<FormProperty> formProperties = taskFormData.getFormProperties();
                Map<String, Object> variables = Maps.newHashMap();
                for (FormProperty property : formProperties) {
                    String line = null;
                    if (StringFormType.class.isInstance(property.getType())) {
                        LOGGER.error("请输入{}:", property.getName());
                        line = scanner.nextLine();
                        variables.put(property.getId(), line);
                    } else if (DateFormType.class.isInstance(property.getType())) {
                        LOGGER.error("请输入{}格式（yyyy-MM-dd）", property.getName());
                        line = scanner.nextLine();
                        variables.put(property.getId(), new SimpleDateFormat("yyyy-MM-dd").parse(line));
                    } else {
                        LOGGER.error("类型不支持", property.getType());
                    }
                    LOGGER.error("您输入的内容是：{}", line);
                }
                taskService.complete(task.getId(), variables);
            }
            processInstance =
                    processEngine.getRuntimeService().
                            createProcessInstanceQuery().
                            processInstanceId(processInstance.getId())
                            .singleResult();
        }

        scanner.close();
    }

    private static ProcessEngine getProcessEngine() {
        ProcessEngineConfiguration cfg = ProcessEngineConfiguration
                .createStandaloneInMemProcessEngineConfiguration();
        cfg.setDatabaseSchemaUpdate("true");// 防止activiti创建23张表报错
        ProcessEngine processEngine = cfg.buildProcessEngine();
        String name = processEngine.getName();
        String version = processEngine.VERSION;
        LOGGER.error(name + ":" + version);
        return processEngine;
    }

    private static ProcessDefinition getProcessDefinition(ProcessEngine processEngine) {
        RepositoryService repositoryService = processEngine.getRepositoryService();
        DeploymentBuilder deploymentBuilder = repositoryService.createDeployment();
        deploymentBuilder.addClasspathResource("second-approve.bpmn20.xml");
        Deployment deploy = deploymentBuilder.deploy();
        String id = deploy.getId();
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().deploymentId(id).singleResult();
        LOGGER.error("流程定义文件{}，流程定义ID{}", processDefinition.getName(), processDefinition.getId());
        return processDefinition;
    }

    private static ProcessInstance getProcessEngine(ProcessEngine processEngine, ProcessDefinition processDefinition) {
        RuntimeService runtimeService = processEngine.getRuntimeService();
        ProcessInstance processInstance = runtimeService.startProcessInstanceById(processDefinition.getId());
        LOGGER.error("启动流程 {}", processInstance.getProcessDefinitionKey());
        return processInstance;
    }

}