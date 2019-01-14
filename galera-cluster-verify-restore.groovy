/**
 * Verify and restore Galera cluster
 *
 * Expected parameters:
 *   SALT_MASTER_CREDENTIALS    Credentials to the Salt API.
 *   SALT_MASTER_URL            Full Salt API address [http://10.10.10.1:8000].
 *
**/

def common = new com.mirantis.mk.Common()
def salt = new com.mirantis.mk.Salt()
def openstack = new com.mirantis.mk.Openstack()
def python = new com.mirantis.mk.Python()

def pepperEnv = "pepperEnv"
def resultCode = 99

timeout(time: 12, unit: 'HOURS') {
    node() {
        stage('Setup virtualenv for Pepper') {
            python.setupPepperVirtualenv(pepperEnv, SALT_MASTER_URL, SALT_MASTER_CREDENTIALS)
        }
        stage('Verify status')
            resultCode = openstack.verifyGaleraStatus(pepperEnv, false)
        stage('Restore') {
            if (resultCode == 128) {
                common.errorMsg("Unable to connect to Galera Master. Trying slaves...")
                resultCode = openstack.verifyGaleraStatus(pepperEnv, true)
                if (resultCode == 129) {
                    common.errorMsg("Unable to obtain Galera slave minions list". "Without fixing this issue, pipeline cannot continue in verification and restoration.")
                    currentBuild.result = "FAILURE"
                    return
                } else if (resultCode == 130) {
                    common.errorMsg("Neither master or slaves are reachable. Without fixing this issue, pipeline cannot continue in verification and restoration.")
                    currentBuild.result = "FAILURE"
                    return
                }
            }
            if (resultCode == 1) {
                common.warningMsg("There was a problem with parsing the status output or with determining it. Do you want to run a restore?")
            } else if (resultCode > 1) {
                common.warningMsg("There's something wrong with the cluster, do you want to run a restore?")
            } else {
                common.warningMsg("There seems to be everything alright with the cluster, do you still want to run a restore?")
            }
            input message: "Are you sure you want to run a restore? Click to confirm"
            try {
                openstack.restoreGaleraDb(pepperEnv)
            } catch (Exception e) {
                common.errorMsg("Restoration process has failed.")
            }
        }
    }
}