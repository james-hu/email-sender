package net.sf.jabb.email.sender;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;

/**
 * The AWS Lambda request handler that takes input from SQS and send them out through SES.
 */
public class LambdaEmailSender implements RequestHandler<SQSEvent, Boolean> {
    public static final String ENV_NAME_SES_REGION = "SES_REGION";
    public static final String ENV_NAME_DLQ_URL = "DLQ_URL";

    private static final Logger logger = LogManager.getLogger(LambdaEmailSender.class);
    private static ObjectMapper objectMapper = new ObjectMapper();

    public Boolean handleRequest(SQSEvent event, Context context) {
        // initialization for all messages
        String region = System.getenv(ENV_NAME_SES_REGION);
        if (region == null || region.length() == 0){
            logger.error("Please specify through which AWS region should emails be sent in environment variable '{}'",
                    ENV_NAME_SES_REGION);
            throw new IllegalArgumentException("Environment Variable '" + ENV_NAME_SES_REGION + "' is not set");
        }
        logger.debug("Using AWS region '{}' for sending emails", region);
        AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.standard()
                .withRegion(region)
                .build();

        // loop through all messages
        boolean allSucceeded = true;
        int invalidJsonCount = 0;
        int sentCount = 0;
        for(SQSEvent.SQSMessage eventMsg : event.getRecords()){
            String eventMsgBody = eventMsg.getBody();
            if (eventMsgBody != null && eventMsgBody.length() > 0) {
                // parse
                MessageToSend emailMsg = null;
                try {
                    emailMsg = objectMapper.readValue(eventMsgBody, MessageToSend.class);
                } catch (Exception e) {
                    invalidJsonCount ++;
                    allSucceeded = false;
                    logger.error("Failed to parse into JSON, will never retry. Source: {}, Id: {}, Error: {}, Body: {}",
                            eventMsg.getEventSourceArn(), eventMsg.getMessageId(), e.getMessage(), eventMsgBody);
                    copyQuietly(eventMsg, "JSON Parsing Error: " + e.getMessage());
                    continue;
                }

                // send
                try{
                    send(client, emailMsg);
                    sentCount ++;
                }catch(Exception e){
                    allSucceeded = false;
                    logger.error("Failed to send email, will never retry. Source: {}, Id: {}, Error: {}, From: {}, To: {}, Subject: {}",
                            eventMsg.getEventSourceArn(), eventMsg.getMessageId(), e.getMessage(), emailMsg.getFrom(), emailMsg.getTo(), emailMsg.getSubject());
                    copyQuietly(eventMsg, "Failed to send: " + e.getMessage());
                    continue;
                }
            }
        }
        logger.info("Handled {} in total. Sent: {}, Invalid JSON: {}.", event.getRecords().size(), sentCount, invalidJsonCount);
        return allSucceeded;
    }

    /**
     * Quietly copy the SQS message content into a SQS Queue which normally is a Dead-Letter-Queue.
     * This method does not through Exception in any case.
     * @param eventMsg  the original SQS message received by the Lambda function.
     *                  This is the message to be copied into the Dead-Letter-Queue.
     * @param remark    Content of the 'remark' attribute of the message in the Dead-Letter-Queue.
     *      *                  It might be an description on why the message went to the Dead-Letter-Queue.
     */
    protected void copyQuietly(SQSEvent.SQSMessage eventMsg, String remark){
        String dlqUrl = System.getenv(ENV_NAME_DLQ_URL);
        if (dlqUrl == null || dlqUrl.length() == 0){
            logger.error("Please specify into which SQS queue invalid/failed input should be moved in environment variable '{}'",
                    ENV_NAME_DLQ_URL);
        }else{
            try {
                copy(eventMsg, dlqUrl, remark);
            }catch(Exception e1){
                logger.error("Failed to copy to DLQ. URL: {}, Source: {}, Id: {}, Remark: {}",
                        dlqUrl, eventMsg.getEventSourceArn(), eventMsg.getMessageId(), remark);
            }
        }

    }

    /**
     * Copy the SQS message content into a SQS Queue which normally is a Dead-Letter-Queue.
     * This method might through exception if calling to SQS API fails.
     * @param eventMsg  The original SQS message received by the Lambda function.
     *                  This is the message to be copied into the Dead-Letter-Queue.
     * @param dlqUrl    URL to the Dead-Letter-Queue into which the original message will be copied
     * @param remark    Content of the 'remark' attribute of the message in the Dead-Letter-Queue.
     *                  It might be an description on why the message went to the Dead-Letter-Queue.
     */
    protected void copy(SQSEvent.SQSMessage eventMsg, String dlqUrl, String remark){
        AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
        SendMessageRequest req = new SendMessageRequest()
                .withQueueUrl(dlqUrl)
                .withMessageBody(eventMsg.getBody())
                .addMessageAttributesEntry("OriginalEventSourceArn", new MessageAttributeValue().withDataType("String").withStringValue(eventMsg.getEventSourceArn()))
                .addMessageAttributesEntry("OriginalMessageId", new MessageAttributeValue().withDataType("String").withStringValue(eventMsg.getMessageId()))
                .addMessageAttributesEntry("Remark", new MessageAttributeValue().withDataType("String").withStringValue(remark))
                ;
        final Map<String, MessageAttributeValue> attributes = req.getMessageAttributes();
        eventMsg.getAttributes().forEach((k, v) ->
                attributes.put("OriginalAttr" + k, new MessageAttributeValue().withDataType("String").withStringValue(v)));
        eventMsg.getMessageAttributes().forEach((k, v) ->
                attributes.put("OriginalMsgAttr" + k, new MessageAttributeValue().withDataType("String").withStringValue(v.getStringValue())));
        sqs.sendMessage(req);
    }

    /**
     * Send out email through AWS SES.
     * This method might through exception if calling to SES API fails.
     * @param ses   SES client
     * @param msg   Detail of the Email message to be sent
     */
    protected void send(AmazonSimpleEmailService ses, MessageToSend msg){
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
