//
// Copyright Aliaksei Levin (levlam@telegram.org), Arseny Smirnov (arseny30@gmail.com) 2014-2026
//
// Distributed under the Boost Software License, Version 1.0. (See accompanying
// file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)
//
// Yumeiro (F-02 / ADR-052): TDLib's own JSON-from-Java binding, unchanged except that the library is not loaded in a
// static block. Yumeiro downloads libtdjsonjava.so, checks its SHA-256 and calls System.load itself (TdEngine); the
// library's JNI_OnLoad registers these natives against this class by name, so the package, class and method names
// must stay exactly as they are (kept from R8 in proguard-rules.pro).
//
package org.drinkless.tdlib;

/**
 * Main class for interaction with the TDLib using JSON interface.
 */
public final class JsonClient {
    /**
     * Returns an opaque identifier of a new TDLib instance.
     * The TDLib instance will not send updates until the first request is sent to it.
     * @return Opaque identifier of a new TDLib instance.
     */
    public static native int createClientId();

    /**
     * Sends request to the TDLib client. May be called from any thread.
     * @param clientId TDLib client identifier.
     * @param request JSON-serialized request.
     */
    public static native void send(int clientId, String request);

    /**
     * Receives incoming updates and request responses. Must not be called simultaneously from two different threads.
     * @param timeout The maximum number of seconds allowed for this function to wait for new data.
     * @return JSON-serialized incoming update or request response. May be null if the timeout expired before new data received.
     */
    public static native String receive(double timeout);

    /**
     * Synchronously executes a TDLib request.
     * @param request JSON-serialized request.
     * @return JSON-serialized request response. May be null if the request is invalid.
     */
    public static native String execute(String request);

    /**
     * Interface for handler of messages that are added to the internal TDLib log.
     */
    public interface LogMessageHandler {
        void onLogMessage(int verbosityLevel, String message);
    }

    /**
     * Sets the handler for messages that are added to the internal TDLib log.
     */
    public static native void setLogMessageHandler(int maxVerbosityLevel, JsonClient.LogMessageHandler logMessageHandler);

    private JsonClient() {
    }
}
