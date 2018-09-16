package net.sf.jabb.email.sender;

import com.sendgrid.*;

import java.io.IOException;

/**
 * Service for sending out emails through SendGrid
 */
public class SendGridMessageSendingService implements MessageSendingService {
    public static final String ENV_NAME_SENDGRID_API_KEY = "SENDGRID_API_KEY";

    protected SendGrid sg;

    public SendGridMessageSendingService(){
        String apiKey = System.getenv(ENV_NAME_SENDGRID_API_KEY);
        if (apiKey == null || apiKey.length() == 0){
            throw new IllegalArgumentException("Please specify SendGrid API key in environment variable: " + ENV_NAME_SENDGRID_API_KEY);

        }
        sg = new SendGrid(apiKey);
    }

    @Override
    public void send(MessageToSend msg) throws IOException, IllegalStateException {
        Content content = msg.getBodyHtml() != null ?
                new Content("text/html", msg.getBodyHtml()) :
                new Content("text/plain", msg.getBodyText());
        Mail mail = new Mail(new Email(msg.getFrom()), msg.getSubject(), new Email(msg.getTo()), content);

        com.sendgrid.Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());
        Response response = sg.api(request);
        if (response.getStatusCode() % 100 != 2){
            throw new IllegalStateException("Failed to send to SendGrid. Status: " + response.getStatusCode() + ", Body: " + response.getBody());
        }
    }
}
