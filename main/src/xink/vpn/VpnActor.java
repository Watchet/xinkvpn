package xink.vpn;

import static xink.vpn.Constants.*;

import java.io.Serializable;
import java.util.List;

import xink.vpn.wrapper.VpnManager;
import xink.vpn.wrapper.VpnProfile;
import xink.vpn.wrapper.VpnService;
import xink.vpn.wrapper.VpnState;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.util.Log;

public class VpnActor {

    private static final String TAG = "xink";

    private VpnProfileRepository repository;
    private VpnManager vpnMgr;
    private VpnService vpnSrv;
    private Context context;

    public VpnActor(final Context ctx) {
        super();
        context = ctx;
    }

    public void connect() {
        final VpnProfile p = getRepository().getActiveProfile();
        if (p == null) {
            throw new NoActiveVpnException("connect failed, no active vpn");
        }

        connect(p);
    }

    public void connect(final VpnProfile p) {
        Log.i(TAG, "connect to: " + p);

        p.preConnect();
        final VpnProfile cp = p.dulicateToConnect(); // connect using a clone, so the secret key can be replace

        getVpnMgr().startVpnService();
        ServiceConnection c = new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName className, final IBinder service) {
                try {
                    boolean success = getVpnSrv().connect(service, cp);

                    if (!success) {
                        Log.d(TAG, "~~~~~~ connect() failed!");
                        broadcastConnectivity(cp.getName(), VpnState.IDLE, VPN_ERROR_CONNECTION_FAILED);
                    } else {
                        Log.d(TAG, "~~~~~~ connect() succeeded!");
                    }
                } catch (Throwable e) {
                    Log.e(TAG, "connect()", e);
                    broadcastConnectivity(cp.getName(), VpnState.IDLE, VPN_ERROR_CONNECTION_FAILED);
                } finally {
                    context.unbindService(this);
                }
            }

            @Override
            public void onServiceDisconnected(final ComponentName className) {
                Log.e(TAG, "onServiceDisconnected");
                checkStatus();
            }
        };

        if (!getVpnMgr().bindVpnService(c)) {
            Log.e(TAG, "bind service failed");
            broadcastConnectivity(cp.getName(), VpnState.IDLE, VPN_ERROR_CONNECTION_FAILED);
        }
    }

    public void disconnect() {
        Log.i(TAG, "disconnect active vpn");

        ServiceConnection c = new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName className, final IBinder service) {
                try {
                    getVpnSrv().disconnect(service);
                } catch (Exception e) {
                    Log.e(TAG, "disconnect()", e);
                    checkStatus();
                } finally {
                    context.unbindService(this);
                }
            }

            @Override
            public void onServiceDisconnected(final ComponentName className) {
                Log.e(TAG, "onServiceDisconnected");
                checkStatus();
            }
        };
        if (!getVpnMgr().bindVpnService(c)) {
            Log.e(TAG, "bind service failed");
            checkStatus();
        }
    }

    public void checkStatus() {
        final VpnProfile p = getRepository().getActiveProfile();
        if (p == null) {
            return;
        }

        checkStatus(p);
    }

    private void checkStatus(final VpnProfile p) {
        Log.i(TAG, "check status of vpn: " + p);

        final ConditionVariable cv = new ConditionVariable();
        cv.close();

        ServiceConnection c = new ServiceConnection() {
            @Override
            public synchronized void onServiceConnected(final ComponentName className, final IBinder service) {
                cv.open();
                try {
                    getVpnSrv().checkStatus(service, p);
                } catch (Exception e) {
                    Log.e(TAG, "checkStatus()", e);
                    broadcastConnectivity(p.getName(), VpnState.IDLE, VPN_ERROR_NO_ERROR);
                } finally {
                    context.unbindService(this);
                }
            }

            @Override
            public void onServiceDisconnected(final ComponentName className) {
                cv.open();
                broadcastConnectivity(p.getName(), VpnState.IDLE, VPN_ERROR_NO_ERROR);
                context.unbindService(this);
            }
        };
        if (getVpnMgr().bindVpnService(c)) {
            // wait for a second, let status propagate
            if (!cv.block(1000)) {
                broadcastConnectivity(p.getName(), VpnState.IDLE, VPN_ERROR_NO_ERROR);
            }
        }
    }

    public void checkAllStatus() {
        List<VpnProfile> profiles = getRepository().getAllVpnProfiles();
        for (VpnProfile p : profiles) {
            checkStatus(p);
        }
    }

    private VpnProfileRepository getRepository() {
        if (repository == null) {
            repository = VpnProfileRepository.getInstance(context);
        }

        return repository;
    }

    private VpnManager getVpnMgr() {
        if (vpnMgr == null) {
            vpnMgr = new VpnManager(context);
        }
        return vpnMgr;
    }

    private VpnService getVpnSrv() {

        if (vpnSrv == null) {
            vpnSrv = new VpnService(context);
        }
        return vpnSrv;
    }

    public void activate(final VpnProfile p) {
        getRepository().setActiveProfile(p);
        broadcastConnectivity(p.getName(), p.getState(), VPN_ERROR_NO_ERROR);
    }

    public void broadcastConnectivity(final String profileName, final VpnState s, final int error) {
        Intent intent = new Intent(ACTION_VPN_CONNECTIVITY);
        intent.putExtra(BROADCAST_PROFILE_NAME, profileName);
        intent.putExtra(BROADCAST_CONNECTION_STATE, s);
        if (error != VPN_ERROR_NO_ERROR) {
            intent.putExtra(BROADCAST_ERROR_CODE, error);
        }
        context.sendBroadcast(intent);
    }

    public static VpnState extractVpnState(final Intent intent) {
        Serializable obj = intent.getSerializableExtra(BROADCAST_CONNECTION_STATE);
        VpnState state = VpnState.IDLE;

        if (obj != null) {
            state = VpnState.valueOf(obj.toString());
        }
        return state;
    }

    public static boolean isInStableState(final VpnProfile p) {
        VpnState state = p.getState();
        return state == VpnState.CONNECTED || state == VpnState.IDLE;
    }
}