package me.eigenein.smarthome.core

import android.net.nsd.NsdManager
import android.util.Log
import io.reactivex.BackpressureStrategy
import io.reactivex.Completable
import io.reactivex.Flowable
import me.eigenein.smarthome.extensions.*
import java.net.DatagramSocket
import java.net.SocketException

class DeviceManager {

    private val socket = DatagramSocket()

    companion object {
        private const val DATAGRAM_SIZE = 256
        private const val SERVICE_TYPE = "_smart-home._udp."

        private val TAG = DeviceManager::class.java.simpleName

        fun discover(nsdManager: NsdManager): Flowable<DeviceAddress> = nsdManager
            .discoverServicesFlowable(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD)
            .flatMap {
                nsdManager
                    .resolveServiceSingle(it)
                    .toFlowable()
                    .onErrorResumeNext { throwable: Throwable ->
                        Log.e(TAG, "Failed to resolve service: $it", throwable)
                        Flowable.empty()
                    }
            }
            .map { DeviceAddress(it.host, it.port) }
    }

    fun listen(): Flowable<Response> = Flowable.create({
        it.setCancellable { socket.close() }
        while (true) {
            try {
                socket.receive(DATAGRAM_SIZE).toResponse().emitTo(it)
            } catch (throwable: SocketException) {
                Log.w(TAG, throwable.message)
                it.onComplete()
                break
            } catch (throwable: Exception) {
                Log.e(TAG, "Failed to receive a response.", throwable)
            }
        }
    }, BackpressureStrategy.BUFFER)

    fun send(request: Request): Completable = Completable.create {
        request.toDatagramPacket(request.address.address, request.address.port).sendTo(socket)
        it.onComplete()
    }
}
