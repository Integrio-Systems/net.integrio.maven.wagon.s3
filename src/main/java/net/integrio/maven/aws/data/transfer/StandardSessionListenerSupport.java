
package net.integrio.maven.aws.data.transfer;

import net.integrio.maven.aws.data.SessionListenerSupport;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.SessionEvent;
import org.apache.maven.wagon.events.SessionListener;

import java.util.HashSet;
import java.util.Set;

public final class StandardSessionListenerSupport implements SessionListenerSupport {

    private final Wagon wagon;

    private final Set<SessionListener> sessionListeners = new HashSet<>();

    public StandardSessionListenerSupport(Wagon wagon) {
        this.wagon = wagon;
    }

    @Override
    public void addSessionListener(SessionListener sessionListener) {
        this.sessionListeners.add(sessionListener);
    }

    @Override
    public void removeSessionListener(SessionListener sessionListener) {
        this.sessionListeners.remove(sessionListener);
    }

    @Override
    public boolean hasSessionListener(SessionListener sessionListener) {
        return this.sessionListeners.contains(sessionListener);
    }

    @Override
    public void fireSessionOpening() {
        SessionEvent event = new SessionEvent(this.wagon, SessionEvent.SESSION_OPENING);
        for (SessionListener sessionListener : this.sessionListeners) {
            sessionListener.sessionOpening(event);
        }
    }

    @Override
    public void fireSessionOpened() {
        SessionEvent event = new SessionEvent(this.wagon, SessionEvent.SESSION_OPENED);
        for (SessionListener sessionListener : this.sessionListeners) {
            sessionListener.sessionOpened(event);
        }
    }

    @Override
    public void fireSessionDisconnecting() {
        SessionEvent event = new SessionEvent(this.wagon, SessionEvent.SESSION_DISCONNECTING);
        for (SessionListener sessionListener : this.sessionListeners) {
            sessionListener.sessionDisconnecting(event);
        }
    }

    @Override
    public void fireSessionDisconnected() {
        SessionEvent event = new SessionEvent(this.wagon, SessionEvent.SESSION_DISCONNECTED);
        for (SessionListener sessionListener : this.sessionListeners) {
            sessionListener.sessionDisconnected(event);
        }
    }

    @Override
    public void fireSessionConnectionRefused() {
        SessionEvent event = new SessionEvent(this.wagon, SessionEvent.SESSION_CONNECTION_REFUSED);
        for (SessionListener sessionListener : this.sessionListeners) {
            sessionListener.sessionConnectionRefused(event);
        }
    }

    @Override
    public void fireSessionLoggedIn() {
        SessionEvent event = new SessionEvent(this.wagon, SessionEvent.SESSION_LOGGED_IN);
        for (SessionListener sessionListener : this.sessionListeners) {
            sessionListener.sessionLoggedIn(event);
        }
    }

    @Override
    public void fireSessionLoggedOff() {
        SessionEvent event = new SessionEvent(this.wagon, SessionEvent.SESSION_LOGGED_OFF);
        for (SessionListener sessionListener : this.sessionListeners) {
            sessionListener.sessionLoggedOff(event);
        }
    }

    @Override
    public void fireSessionError(Exception exception) {
        SessionEvent event = new SessionEvent(this.wagon, exception);
        for (SessionListener sessionListener : this.sessionListeners) {
            sessionListener.sessionError(event);
        }
    }
}
