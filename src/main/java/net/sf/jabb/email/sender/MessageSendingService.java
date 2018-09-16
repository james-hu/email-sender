package net.sf.jabb.email.sender;

/**
 * The service that can send out messages.
 */
public interface MessageSendingService {
    /**
     * Hand over the message to the service for sending out.
     * This method might throw exception if calling to service provider's API fails.
     * @param msg   Detail of the Email message to be sent
     * @throws Exception    If failed to start sending the message.
     *                      Please note that a successful return of this method does not mean the message had been already delivered.
     */
    void send(MessageToSend msg) throws Exception;
}
