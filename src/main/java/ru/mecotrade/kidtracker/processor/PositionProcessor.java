package ru.mecotrade.kidtracker.processor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.mecotrade.kidtracker.controller.model.Report;
import ru.mecotrade.kidtracker.controller.model.Snapshot;
import ru.mecotrade.kidtracker.controller.model.Position;
import ru.mecotrade.kidtracker.dao.MessageService;
import ru.mecotrade.kidtracker.dao.UserService;
import ru.mecotrade.kidtracker.dao.model.Kid;
import ru.mecotrade.kidtracker.dao.model.Message;
import ru.mecotrade.kidtracker.dao.model.User;
import ru.mecotrade.kidtracker.device.Device;
import ru.mecotrade.kidtracker.device.DeviceManager;
import ru.mecotrade.kidtracker.exception.KidTrackerParseException;
import ru.mecotrade.kidtracker.exception.KidTrackerUnknownUserException;
import ru.mecotrade.kidtracker.util.MessageUtils;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class PositionProcessor {

    @Autowired
    private UserService userService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private DeviceManager deviceManager;

    private Map<String, Map<LocalDate, Integer>> initPedometers;

    public Report report(Long userId) throws KidTrackerUnknownUserException {
        // TODO use cache
        Optional<User> user = userService.get(userId);
        if (user.isPresent()) {
            Collection<Device> devices = deviceManager.select(user.get().getKids().stream().map(Kid::getDeviceId).collect(Collectors.toList()));

            Collection<Position> positions = devices.stream()
                    .map(Device::position)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // if there are some devices with unknown last location, try to find it in the database
            if (positions.size() < devices.size()) {
                for (Device device : devices.stream().filter(d -> d.getLocation() == null).collect(Collectors.toList())) {
                    synchronized (device) {

                        if (device.getLocation() == null) {
                            Message message = messageService.last(device.getId(), MessageUtils.LOCATION_TYPES, Message.Source.DEVICE);
                            log.debug("[{}] Location not found, use historical message {}", device.getId(), message);
                            if (message != null) {
                                try {
                                    device.setLocation(MessageUtils.toLocation(message));
                                    positions.add(device.position());
                                } catch (KidTrackerParseException ex) {
                                    log.warn("Unable to parse historical location message {}", message);
                                }
                            }
                        }
                    }
                }
            }

            Collection<Snapshot> snapshots = devices.stream()
                    .map(Device::snapshot)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // if there are some devices with unknown last link, try to find it in the database
            if (snapshots.size() < devices.size()) {
                for (Device device : devices.stream().filter(d -> d.getLink() == null).collect(Collectors.toList())) {
                    synchronized (device) {
                        if (device.getLink() == null) {
                            Message message = messageService.last(device.getId(), Collections.singleton(MessageUtils.LINK_TYPE), Message.Source.DEVICE);
                            log.debug("[{}] Link not found, use historical message {}", device.getId(), message);
                            try {
                                device.setLink(MessageUtils.toLink(message));
                                snapshots.add(device.snapshot());
                            } catch (KidTrackerParseException ex) {
                                log.warn("Unable to parse historical link message {}", message);
                            }
                        }
                    }
                }
            }

            return Report.of(positions, snapshots);
        } else {
            throw new KidTrackerUnknownUserException(String.valueOf(userId));
        }
    }

    public Collection<Snapshot> snapshot(Long userId, Date timestamp) throws KidTrackerUnknownUserException {
        // TODO use cache
        Optional<User> user = userService.get(userId);
        if (user.isPresent()) {
            Collection<String> deviceIds = user.get().getKids().stream().map(Kid::getDeviceId).collect(Collectors.toList());
            return messageService.last(deviceIds,
                    Stream.concat(MessageUtils.LOCATION_TYPES.stream(), Stream.of(MessageUtils.LINK_TYPE)).collect(Collectors.toList()),
                    Message.Source.DEVICE,
                    timestamp).stream().map(MessageUtils::toSnapshot).collect(Collectors.toList());
        } else {
            throw new KidTrackerUnknownUserException(String.valueOf(userId));
        }
    }

    public Optional<Snapshot> snapshot(String deviceId, Date timestamp) {
        return messageService.last(Collections.singletonList(deviceId),
                Stream.concat(MessageUtils.LOCATION_TYPES.stream(), Stream.of(MessageUtils.LINK_TYPE)).collect(Collectors.toList()),
                Message.Source.DEVICE,
                timestamp).stream().map(MessageUtils::toSnapshot).findFirst();
    }

    public Collection<Position> path(String deviceId, Long start, Long end) {
        return messageService.slice(deviceId, MessageUtils.LOCATION_TYPES, Message.Source.DEVICE, new Date(start), new Date(end)).stream()
                .map(MessageUtils::toPosition)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
