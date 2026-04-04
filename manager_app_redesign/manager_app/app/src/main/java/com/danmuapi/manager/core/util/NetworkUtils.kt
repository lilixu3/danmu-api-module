package com.danmuapi.manager.core.util

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

@Composable
fun rememberLanIpv4Addresses(): List<String> {
    val context = LocalContext.current
    val connectivityManager = remember(context) {
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    var ips by remember { mutableStateOf(getLanIpv4Addresses()) }

    DisposableEffect(connectivityManager) {
        if (connectivityManager == null) {
            ips = getLanIpv4Addresses()
            onDispose { }
        } else {
            val refresh = { ips = getLanIpv4Addresses() }
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = refresh()
                override fun onLost(network: Network) = refresh()
                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = refresh()
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = refresh()
            }

            refresh()
            try {
                connectivityManager.registerDefaultNetworkCallback(callback)
            } catch (_: Throwable) {
            }

            onDispose {
                try {
                    connectivityManager.unregisterNetworkCallback(callback)
                } catch (_: Throwable) {
                }
            }
        }
    }

    return ips
}

fun getLanIpv4Addresses(): List<String> {
    return try {
        val result = mutableListOf<String>()
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        for (networkInterface in interfaces) {
            if (!networkInterface.isUp || networkInterface.isLoopback) continue

            val name = networkInterface.name ?: ""
            if (name.startsWith("lo") || name.startsWith("tun") || name.startsWith("dummy")) continue

            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    val ip = address.hostAddress ?: continue
                    if (!ip.startsWith("169.254.")) {
                        result += ip
                    }
                }
            }
        }
        result
            .distinct()
            .sortedWith(
                compareBy<String> {
                    when {
                        it.startsWith("192.168.") -> 0
                        it.startsWith("10.") -> 1
                        it.startsWith("172.16.") -> 2
                        else -> 3
                    }
                }.thenBy { it },
            )
    } catch (_: Throwable) {
        emptyList()
    }
}
