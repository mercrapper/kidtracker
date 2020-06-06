package ru.mecotrade.babytracker.device;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.mecotrade.babytracker.protocol.MessageParser;
import ru.mecotrade.babytracker.protocol.MessageProcessor;

import java.net.Socket;

@Component
public class MessageListenerFactory {

    @Autowired
    private MessageParser parser;

    @Autowired
    private DeviceManager deviceManager;

    @Autowired
    private MessageProcessor messageProcessor;

    public MessageListener getDeviceListener(String guid, Socket socket) {
        return new MessageListener(guid, socket, parser, deviceManager, messageProcessor);
    }
}
