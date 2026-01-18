package com.danmuapi.manager.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Listen to system network changes (no polling) and keep LAN IPv4 addresses updated.
 *
 * This is best-effort: on some devices/ROMs, interface updates may be delayed.
 */
@Composable
fun rememberLanIpv4Addresses(): List<String> {
    val context = LocalContext.current
    val cm = remember(context) {
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    var ips by remember { mutableStateOf(getLanIpv4Addresses()) }

    DisposableEffect(cm) {
        if (cm == null) {
            ips = getLanIpv4Addresses()
            onDispose { }
        } else {
            val refresh = {
                ips = getLanIpv4Addresses()
            }

            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = refresh()
                override fun onLost(network: Network) = refresh()

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = refresh()

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = refresh()
            }

            // Initial refresh
            refresh()

            try {
                cm.registerDefaultNetworkCallback(cb)
            } catch (_: Throwable) {
                // Some ROMs may throw without ACCESS_NETWORK_STATE.
                // We'll just fall back to the initial value.
            }

            onDispose {
                try {
                    cm.unregisterNetworkCallback(cb)
                } catch (_: Throwable) {
                }
            }
        }
    }

    return ips
}

/**
 * Enumerate current IPv4 addresses for non-loopback UP interfaces.
 */
fun getLanIpv4Addresses(): List<String> {
    return try {
        val result = mutableListOf<String>()
        val ifaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        for (iface in ifaces) {
            // Skip down interfaces / loopback / tunnels.
            if (!iface.isUp || iface.isLoopback) continue
            val name = iface.name ?: ""
            if (name.startsWith("lo") || name.startsWith("tun") || name.startsWith("dummy")) continue

            val addrs = iface.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val ip = addr.hostAddress ?: continue
                    // Skip link-local (169.254.x.x)
                    if (ip.startsWith("169.254.")) continue
                    result.add(ip)
                }
            }
        }
        // Prefer common private ranges first.
        result.distinct().sortedWith(
            compareBy<String> {
                when {
                    it.startsWith("192.168.") -> 0
                    it.startsWith("10.") -> 1
                    it.startsWith("172.16.") -> 2
                    else -> 3
                }
            }.thenBy { it }
        )
    } catch (_: Throwable) {
        emptyList()
    }
}
