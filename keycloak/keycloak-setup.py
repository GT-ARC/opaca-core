import optparse
from keycloak import KeycloakAdmin


def get_admin_client(realm=None):
    return KeycloakAdmin(
        server_url="http://localhost:9000",
        username='admin',
        password='admin',
        realm_name=realm or 'master',
        user_realm_name='master',
        verify=True,
    )

def create_realm(kc, realm):
    if not any(r["realm"] == realm for r in kc.get_realms()):
        # 'skip_exists' parameter is bugged for this method...
        kc.create_realm({
            "realm": realm,
            "enabled": True
        })

def create_client(kc, clientId, public=True):
    kc.create_client({
        "clientId": clientId,
        "enabled": True,
        "protocol": "openid-connect",
        "publicClient": public
    }, skip_exists=True)

def create_role(kc, name):
    kc.create_realm_role({
        "name": name
    }, skip_exists=True)

def create_user(kc, username, password, role=None, email=None, first=None, last=None):
    user_id = kc.create_user({
        "username": username,
        "enabled": True,
        "email": email or f"{username}@example.com",
        "firstName": first or username,
        "lastName": last or username,
        "credentials": [{"type": "password", "value": password}]
    }, exist_ok=True)
    if role:
        role_repr = next(r for r in kc.get_realm_roles() if r["name"] == role)
        kc.assign_realm_roles(user_id, [role_repr])


def main():
    parser = optparse.OptionParser(description="Script for initializing Keycloak for OPACA Runtime Platform")
    parser.add_option('-u', '--users', action='store_true', help="Create dummy users for Unit Tests")
    opts, _ = parser.parse_args()

    kc_master = get_admin_client()
    kc_opaca = get_admin_client("opaca")

    # realm & client
    print("Creating realm and client...")
    create_realm(kc_master, "opaca")
    create_client(kc_opaca, "opaca-rp")
    # roles
    print("Creating roles...")
    create_role(kc_opaca, "MANAGER")
    create_role(kc_opaca, "CONTRIBUTOR")
    create_role(kc_opaca, "USER")
    create_role(kc_opaca, "GUEST")

    if opts.users:
        # users for unit tests
        print("Creating test users...")
        create_user(kc_opaca, "guest", "12345", "GUEST")
        create_user(kc_opaca, "user1", "12345", "USER")
        create_user(kc_opaca, "user2", "12345", "USER")
        create_user(kc_opaca, "contributor1", "12345", "CONTRIBUTOR")
        create_user(kc_opaca, "contributor2", "12345", "CONTRIBUTOR")
        create_user(kc_opaca, "manager", "12345", "MANAGER")


main()
