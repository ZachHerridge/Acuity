package com.acuitybotting.bot.launcher.services;

import com.acuitybotting.bot.launcher.ui.LauncherFrame;
import com.acuitybotting.bot.launcher.utils.CommandLine;
import com.acuitybotting.common.utils.configurations.utils.ConnectionConfigurationUtil;
import com.acuitybotting.common.utils.configurations.ConnectionConfiguration;
import com.acuitybotting.data.flow.messaging.services.client.exceptions.MessagingException;
import com.acuitybotting.data.flow.messaging.services.client.implementation.rabbit.RabbitHub;
import com.acuitybotting.data.flow.messaging.services.events.MessageEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

/**
 * Created by Zachary Herridge on 8/6/2018.
 */
@Service
@Slf4j
public class LauncherRabbitService implements CommandLineRunner {

    private final StateService stateService;
    private RabbitHub rabbitHub = new RabbitHub();

    private Gson gson = new Gson();

    private String connectionKey;
    private String masterPassword;

    @Autowired
    public LauncherRabbitService(StateService stateService) {
        this.stateService = stateService;
    }

    public void connect(String connectionKey) {
        this.connectionKey = connectionKey;
        try {

            JsonObject jsonObject = ConnectionConfigurationUtil.decodeConnectionKey(connectionKey);
            String username = jsonObject.get("principalId").getAsString();
            String password = jsonObject.get("secret").getAsString();

            rabbitHub.auth(username, password);
            rabbitHub.start();
            rabbitHub.getLocalQueue().withListener(this::handleMessage);

            updateState();
        } catch (Throwable e) {
            log.error("Error during dashboard RabbitMQ setup.", e);
        }
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 10000)
    private void updateState(){
        try {
            if (rabbitHub.getLocalQueue() == null) return;
            rabbitHub.updateConnectionDocument("state", new Gson().toJsonTree(stateService.buildState()).getAsJsonObject());
            log.info("Updated state.");
        } catch (MessagingException e) {
            log.error("Error updating state.", e);
        }
    }

    private void handleMessage(MessageEvent messageEvent) {
        if ("runCommand".equals(messageEvent.getMessage().getAttributes().get("type"))) {
            JsonObject launchConfig = gson.fromJson(messageEvent.getMessage().getBody(), JsonObject.class);
            
            log.info("Got launch config: ", launchConfig);
            String command = CommandLine.replacePlaceHolders(launchConfig.get("command").getAsString());

            ConnectionConfiguration connectionConfiguration = ConnectionConfigurationUtil.decode(launchConfig.get("acuityConnectionConfiguration").getAsString()).orElse(new ConnectionConfiguration());
            if (connectionConfiguration.getConnectionKey() == null) connectionConfiguration.setConnectionKey(connectionKey);
            if (connectionConfiguration.getMasterKey() == null) connectionConfiguration.setMasterKey(masterPassword);

            command = command.replaceAll("\\{CONNECTION}", ConnectionConfigurationUtil.encode(connectionConfiguration));

            log.info("Running command: {}", command);

            try {
                CommandLine.runCommand(command);
            } catch (Throwable e) {
                log.error("Error running command.", e);
            }
        }
    }

    @Override
    public void run(String... strings) {
        Optional<ConnectionConfiguration> decode = ConnectionConfigurationUtil.decode(ConnectionConfigurationUtil.find());

        LauncherFrame launcherFrame = new LauncherFrame() {

            @Override
            public void onConnect(String connectionKey, String masterPassword) {
                LauncherRabbitService.this.masterPassword = masterPassword;
                connect(connectionKey);
            }

            @Override
            public void onSave(String connectionKey, String masterPassword) {
                LauncherRabbitService.this.masterPassword = masterPassword;
                ConnectionConfiguration connectionConfiguration = new ConnectionConfiguration();
                connectionConfiguration.setConnectionKey(connectionKey);
                connectionConfiguration.setMasterKey(masterPassword);
                ConnectionConfigurationUtil.write(connectionConfiguration);
            }
        };

        launcherFrame.getConnectionKey().setText(decode.map(ConnectionConfiguration::getConnectionKey).orElse(""));
        launcherFrame.getPasswordField().setText(decode.map(ConnectionConfiguration::getMasterKey).orElse(""));
        launcherFrame.setVisible(true);
    }
}
