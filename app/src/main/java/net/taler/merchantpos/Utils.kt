package net.taler.merchantpos

import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import com.google.android.material.snackbar.BaseTransientBottomBar.ANIMATION_MODE_FADE
import com.google.android.material.snackbar.BaseTransientBottomBar.Duration
import com.google.android.material.snackbar.Snackbar.make

object Utils {

    private const val HEX_CHARS = "0123456789ABCDEF"

    fun hexStringToByteArray(data: String): ByteArray {
        val result = ByteArray(data.length / 2)

        for (i in data.indices step 2) {
            val firstIndex = HEX_CHARS.indexOf(data[i])
            val secondIndex = HEX_CHARS.indexOf(data[i + 1])

            val octet = firstIndex.shl(4).or(secondIndex)
            result[i.shr(1)] = octet.toByte()
        }
        return result
    }


    private val HEX_CHARS_ARRAY = HEX_CHARS.toCharArray()

    @Suppress("unused")
    fun toHex(byteArray: ByteArray): String {
        val result = StringBuffer()

        byteArray.forEach {
            val octet = it.toInt()
            val firstIndex = (octet and 0xF0).ushr(4)
            val secondIndex = octet and 0x0F
            result.append(HEX_CHARS_ARRAY[firstIndex])
            result.append(HEX_CHARS_ARRAY[secondIndex])
        }
        return result.toString()
    }

}

fun View.fadeIn(endAction: () -> Unit = {}) {
    if (visibility == VISIBLE) return
    alpha = 0f
    visibility = VISIBLE
    animate().alpha(1f).withEndAction {
        if (context != null) endAction.invoke()
    }.start()
}

fun View.fadeOut(endAction: () -> Unit = {}) {
    if (visibility == INVISIBLE) return
    animate().alpha(0f).withEndAction {
        if (context == null) return@withEndAction
        visibility = INVISIBLE
        alpha = 1f
        endAction.invoke()
    }.start()
}

fun topSnackbar(view: View, text: CharSequence, @Duration duration: Int) {
    make(view, text, duration)
        .setAnimationMode(ANIMATION_MODE_FADE)
        .setAnchorView(R.id.navHostFragment)
        .show()
}

fun topSnackbar(view: View, @StringRes resId: Int, @Duration duration: Int) {
    topSnackbar(view, view.resources.getText(resId), duration)
}

fun NavDirections.navigate(nav: NavController) = nav.navigate(this)

class CombinedLiveData<T, K, S>(
    source1: LiveData<T>,
    source2: LiveData<K>,
    private val combine: (data1: T?, data2: K?) -> S
) : MediatorLiveData<S>() {

    private var data1: T? = null
    private var data2: K? = null

    init {
        super.addSource(source1) { t ->
            data1 = t
            value = combine(data1, data2)
        }
        super.addSource(source2) { k ->
            data2 = k
            value = combine(data1, data2)
        }
    }

    override fun <S : Any?> addSource(source: LiveData<S>, onChanged: Observer<in S>) {
        throw UnsupportedOperationException()
    }

    override fun <T : Any?> removeSource(toRemote: LiveData<T>) {
        throw UnsupportedOperationException()
    }
}
