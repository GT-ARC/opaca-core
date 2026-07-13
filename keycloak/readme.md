# Minimal Keycloak Setup

This directory is meant to provide a minimal Keycloak setup for local testing and Continuous Integration.
It consists of two parts:

* a Docker Compose file for starting a Keycloak server
* a Python script for initializing a Realm and Client, the expected Roles, as well as a few test users for unit tests

To start the keycloak server, simply run `docker compose up` in this directory, then `Ctrl+C` to stop it; or `docker compose up -d` to start and `docker compose down` to stop.

Afterwards, run `python keycloak-setup.py` to setup an `opaca` realm, an `opaca-rp` client, as well as the expected roles. Use the `--user` parameter to also create some dummy users necessary for the unit tests.

Make sure to use matching values for the realm, client, and admin-credentials in your `.env`. For this test-setup:

    KC_ISSUER_URI="http://localhost:9000/realms/opaca"
    KC_CLIENT="opaca-rp"
    KC_ADMIN="admin"
    KC_ADMIN_PW="admin"

You may also use the first part (without users) to quickly set up your production Keycloak, but you will have to adapt the setting for URL, admin-password, etc.
