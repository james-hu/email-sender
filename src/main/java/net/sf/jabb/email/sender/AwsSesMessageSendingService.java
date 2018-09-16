package net.sf.jabb.email.sender;

import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Service for sending emails through AWS SES
 */
public class AwsSesMessageSendingService implements MessageSendingService{
    private static final Logger logger = LogManager.getLogger(AwsSesMessageSendingService.class);

    public static final String ENV_NAME_SES_REGION = "SES_REGION";

    protected AmazonSimpleEmailService ses;

    public AwsSesMessageSendingService(){
        // initialization for all messages
        String region = System.getenv(ENV_NAME_SES_REGION);
        if (region == null || region.length() == 0){
            throw new IllegalArgumentException("Please specify through which AWS region should emails be sent in environment variable: " + ENV_NAME_SES_REGION);
        }
        logger.debug("Using AWS region '{}' for sending emails", region);
        ses = AmazonSimpleEmailServiceClientBuilder.standard()
                .withRegion(region)
                .build();
    }

    @Override
    public void send(MessageToSend msg) {
        SendEmailRequest request = new SendEmailRequest()
                .withSource(msg.getFrom())
                .withDestination(new Destination())
                .withMessage(new Message()
                        .withSubject(new Content().withCharset("UTF-8").withData(msg.getSubject()))
                        .withBody(new Body()));

        // To
        String toAddressesString = msg.getTo();
        Destination dest = request.getDestination();
        if (toAddressesString.indexOf(';') >= 0 || toAddressesString.indexOf(',') >= 0){
            dest.withToAddresses(toAddressesString.split(" *[;,]+ *"));
        }else{
            dest.withToAddresses(toAddressesString);
        }

        // Body
        Body body = request.getMessage().getBody();
        String bodyHtml = msg.getBodyHtml();
        String bodyText = msg.getBodyText();
        if (bodyHtml != null && bodyHtml.length() > 0){
            body.withHtml(new Content().withCharset("UTF-8").withData(bodyHtml));
        }
        if (bodyText != null && bodyText.length() > 0){
            body.withText(new Content().withCharset("UTF-8").withData(bodyText));
        }

        ses.sendEmail(request);
    }
}
