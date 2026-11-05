package in.ruhmans.alliancetrading.notification.service;

public interface PushSender {

    void send(String pushToken, String title, String body);
}
