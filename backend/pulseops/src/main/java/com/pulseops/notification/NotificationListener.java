package com.pulseops.notification;

import com.pulseops.incident.IncidentEvents.IncidentOpened;
import com.pulseops.incident.IncidentEvents.IncidentResolved;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationService notificationService;

    public NotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOpened(IncidentOpened event) {
        safely(event.incidentId(), "INCIDENT_OPENED");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResolved(IncidentResolved event) {
        safely(event.incidentId(), "INCIDENT_RESOLVED");
    }

    /**
     * The incident change is already committed at this point. A failure to create the notification must not
     * propagate back and make the Kafka consumer retry a sample that was processed successfully.
     */
    private void safely(Long incidentId, String type) {
        try {
            notificationService.notifyIncident(incidentId, type);
        } catch (RuntimeException e) {
            log.error("Could not create {} notification for incident {}", type, incidentId, e);
        }
    }
}
