package uk.gov.dwp.carersallowance.sessiondata;

import org.springframework.stereotype.Service;
import uk.gov.dwp.carersallowance.session.NoSessionException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by peterwhitehead on 22/12/2016.
 */
@Service
public class SessionDataServiceImpl implements SessionDataService {
    private Map<String, Session> sessions;

    public SessionDataServiceImpl() {
        sessions = new ConcurrentHashMap<>();
    }

    @Override
    public Session getSessionData(final String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new NoSessionException("No Session for Session ID: " + sessionId);
        }
        return session;
    }

    @Override
    public Session createSessionData(final String sessionId) {
        final Session session = new Session(sessionId);
        sessions.put(sessionId, session);
        return session;
    }

    @Override
    public Session saveSessionData(final Session session) {
        return session;
    }

    @Override
    public Session removeSessionData(final String sessionId) {
        return sessions.remove(sessionId);
    }
}
