

package net.integrio.maven.aws.data;

import org.apache.maven.wagon.events.SessionListener;

public interface SessionListenerSupport {

    /**
     * Add a {@link SessionListener} to be notified
     *
     * @param sessionListener The {@link SessionListener} to be notified
     */
    void addSessionListener(SessionListener sessionListener);

    /**
     * Remove a {@link SessionListener} so that it is no longer notified
     *
     * @param sessionListener The {@link SessionListener} that should no longer be notified
     */
    void removeSessionListener(SessionListener sessionListener);

    /**
     * Returns whether a {@link SessionListener} is already in the collection of {@link SessionListener}s to be notified
     *
     * @param sessionListener The {@link SessionListener} to look for
     * @return {@code true} if the {@link SessionListener} is already in the collection of {@link SessionListener}s to
     * be notified, otherwise {@code false}
     */
    boolean hasSessionListener(SessionListener sessionListener);

    /**
     * Notify {@link SessionListener}s that a session is being opened
     *
     * @see org.apache.maven.wagon.events.SessionEvent#SESSION_OPENING
     */
    void fireSessionOpening();

    /**
     * Notify {@link SessionListener}s that a session has been opened successfully
     *
     * @see org.apache.maven.wagon.events.SessionEvent#SESSION_OPENED
     */
    void fireSessionOpened();

    /**
     * Notify {@link SessionListener}s that a session is being disconnected
     *
     * @see org.apache.maven.wagon.events.SessionEvent#SESSION_DISCONNECTING
     */
    void fireSessionDisconnecting();

    /**
     * Notify {@link SessionListener}s that a session has been disconnected successfully
     *
     * @see org.apache.maven.wagon.events.SessionEvent#SESSION_DISCONNECTED
     */
    void fireSessionDisconnected();

    /**
     * Notify {@link SessionListener}s that creation of the session's connection was refused
     *
     * @see org.apache.maven.wagon.events.SessionEvent#SESSION_CONNECTION_REFUSED
     */
    void fireSessionConnectionRefused();

    /**
     * Notify {@link SessionListener}s that the session was logged in successfully
     *
     * @see org.apache.maven.wagon.events.SessionEvent#SESSION_LOGGED_IN
     */
    void fireSessionLoggedIn();

    /**
     * Notify {@link SessionListener}s that the session was logged off successfully
     *
     * @see org.apache.maven.wagon.events.SessionEvent#SESSION_LOGGED_OFF
     */
    void fireSessionLoggedOff();

    /**
     * Notify {@link SessionListener}s that an error occurred during while the session was in use
     *
     * @param exception The error that occurred
     */
    void fireSessionError(Exception exception);

}
