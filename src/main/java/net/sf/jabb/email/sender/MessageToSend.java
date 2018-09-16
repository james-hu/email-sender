package net.sf.jabb.email.sender;

/**
 * POJO for details of the email message that needs to be sent out.
 */
public class MessageToSend {
    private String from;
    /**
     * Destination(s) of the email message.
     * It may contain multiple email address separated by ';' or ',' characters.
     */
    private String to;
    private String subject;
    private String bodyHtml;
    private String bodyText;

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    /**
     * Get the destination(s) of the email message.
     * It may contain multiple email address separated by ';' or ',' characters.
     * @return  destination(s) of the email message
     */
    public String getTo() {
        return to;
    }

    /**
     * Set the destination(s) of the email message.
     * It may contain multiple email address separated by ';' or ',' characters.
     * @param to destination(s) of the email message
     */
    public void setTo(String to) {
        this.to = to;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }

    public void setBodyHtml(String bodyHtml) {
        this.bodyHtml = bodyHtml;
    }

    public String getBodyText() {
        return bodyText;
    }

    public void setBodyText(String bodyText) {
        this.bodyText = bodyText;
    }
}
