package de.dailab.jiacpp.pingpong;

import java.util.Objects;

/**
 * Temp Java/Lombok class for inter agent communication because at the moment there are some problems
 * with Kotlin data classes and Jackson... also, Lombok does not seem to work here.
 */
interface Messages {

    public static class PingMessage_Java {
        Integer request;

        public PingMessage_Java() {
        }

        public PingMessage_Java(Integer request) {
            this.request = request;
        }

        public void setRequest(Integer request) {
            this.request = request;
        }

        public Integer getRequest() {
            return request;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PingMessage_Java that = (PingMessage_Java) o;
            return Objects.equals(request, that.request);
        }

        @Override
        public int hashCode() {
            return Objects.hash(request);
        }

        @Override
        public String toString() {
            return "PingMessage_Java{" +
                    "request=" + request +
                    '}';
        }
    }

    public static class PongMessage_Java {
        Integer request;
        String agentId;
        Integer offer;

        public PongMessage_Java() {
        }

        public PongMessage_Java(Integer request, String agentId, Integer offer) {
            this.request = request;
            this.agentId = agentId;
            this.offer = offer;
        }

        public Integer getRequest() {
            return request;
        }

        public void setRequest(Integer request) {
            this.request = request;
        }

        public String getAgentId() {
            return agentId;
        }

        public void setAgentId(String agentId) {
            this.agentId = agentId;
        }

        public Integer getOffer() {
            return offer;
        }

        public void setOffer(Integer offer) {
            this.offer = offer;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PongMessage_Java that = (PongMessage_Java) o;
            return Objects.equals(request, that.request) && Objects.equals(agentId, that.agentId) && Objects.equals(offer, that.offer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(request, agentId, offer);
        }

        @Override
        public String toString() {
            return "PongMessage_Java{" +
                    "request=" + request +
                    ", agentId='" + agentId + '\'' +
                    ", offer=" + offer +
                    '}';
        }
    }

}

