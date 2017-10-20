package com.ctrip.xpipe.redis.console.alert.manager;

import com.ctrip.xpipe.api.email.EmailType;
import com.ctrip.xpipe.redis.console.alert.ALERT_TYPE;
import com.ctrip.xpipe.redis.console.alert.AlertChannel;
import com.ctrip.xpipe.redis.console.alert.AlertEntity;
import com.ctrip.xpipe.redis.console.alert.AlertMessageEntity;
import com.ctrip.xpipe.redis.console.alert.policy.SendToDBAAlertPolicy;
import com.ctrip.xpipe.redis.console.alert.sender.Sender;
import com.ctrip.xpipe.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.ctrip.xpipe.redis.console.alert.manager.AlertPolicyManager.EMAIL_CLUSTER_ADMIN;
import static com.ctrip.xpipe.redis.console.alert.manager.AlertPolicyManager.EMAIL_DBA;
import static com.ctrip.xpipe.redis.console.alert.manager.AlertPolicyManager.EMAIL_XPIPE_ADMIN;

/**
 * @author chen.zhu
 * <p>
 * Oct 18, 2017
 */
@Component
public class SenderManager {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    Map<String, Sender> senders;

    @Autowired
    DecoratorManager decoratorManager;

    @Autowired
    SendToDBAAlertPolicy policy;

    public Sender querySender(String id) {
        return senders.get(id);
    }

    public boolean sendAlert(AlertChannel channel, AlertMessageEntity message) {
        String channelId = channel.getId();
        Sender sender = senders.get(channelId);
        try {
            boolean result = sender.send(message);
            logger.info("[sendAlert] Channel: {}, message: {}, send out: {}", channel, message.getTitle(), result);
            return result;
        } catch (Exception e) {
            logger.error("[sendAlert] {}", e);
            return false;
        }
    }

    public boolean sendAlerts(Map<ALERT_TYPE, Set<AlertEntity>> alerts) {
        Map<ALERT_TYPE, Set<AlertEntity>> sendToDBA = new HashMap<>();
        Map<ALERT_TYPE, Set<AlertEntity>> sendToXPipeAdmin = new HashMap<>();
        Map<ALERT_TYPE, Set<AlertEntity>> sendToClusterAdmin = new HashMap<>();
        for(ALERT_TYPE type : alerts.keySet()) {
            if((type.getAlertPolicy() & EMAIL_DBA) != 0) {
                sendToDBA.put(type, alerts.get(type));
            }
            if((type.getAlertPolicy() & EMAIL_XPIPE_ADMIN) != 0) {
                sendToXPipeAdmin.put(type, alerts.get(type));
            }
            if((type.getAlertPolicy() & EMAIL_CLUSTER_ADMIN) != 0) {
                sendToClusterAdmin.put(type, alerts.get(type));
            }
        }
        try {
            return sendToDBA(sendToDBA) &&
            sendToXPipeAdmin(sendToXPipeAdmin) &&
            sendToClusterAdmin(sendToClusterAdmin);
        } catch (Exception e) {
            logger.error("[sendAlerts] {}", e);
            return false;
        }
    }

    private boolean sendToClusterAdmin(Map<ALERT_TYPE, Set<AlertEntity>> sendToClusterAdmin) {
        List<String> emails = new LinkedList<>();
        return sendBatchEmails(sendToClusterAdmin, emails);
    }


    private boolean sendToXPipeAdmin(Map<ALERT_TYPE, Set<AlertEntity>> sendToXPipeAdmin) {
        List<String> emails = policy.getXPipeAdminEmails();
        return sendBatchEmails(sendToXPipeAdmin, emails);
    }

    private boolean sendToDBA(Map<ALERT_TYPE, Set<AlertEntity>> sendToDBA) {
        List<String> emails = policy.getDBAEmails();
        return sendBatchEmails(sendToDBA, emails);
    }
    
    private boolean sendBatchEmails(Map<ALERT_TYPE, Set<AlertEntity>> alerts, List<String> receivers) {
        if(alerts == null || alerts.isEmpty() || receivers == null || receivers.isEmpty()) {
            return true;
        }
        Pair<String, String> pair = decoratorManager.generateTitleAndContent(alerts);
        String title = pair.getKey(), content = pair.getValue();
        AlertMessageEntity message = new AlertMessageEntity(title, EmailType.CONSOLE_ALERT, content, receivers);
        return sendAlert(AlertChannel.MAIL, message);
    }
}
