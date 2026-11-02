package com.dyx.novelreader

import android.app.Application
import android.util.Log
import jcifs.util.Crypto
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** 应用入口：初始化 SMB 认证所需的加密实现 */
class NovelReaderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installNtlmCryptoProvider()
    }

    /**
     * jcifs-ng 用账号密码登录 SMB 时，NTLM 需要 MD4 计算 NT 哈希。
     *
     * jcifs.util.Crypto.getProvider() 是自己 new 一个 BouncyCastleProvider 并缓存，
     * 不会读 Security 提供者列表，因此必须通过 Crypto.initProvider(...) 显式注入；
     * 同时把 Security 里的 "BC" 也换成完整版做兜底。
     *
     * 注意：APK 必须整包保留 org.bouncycastle.**（见 proguard-rules.pro），
     * 否则 BouncyCastle 用字符串注册的 MD4 实现类会被 R8 删掉，
     * 运行时就报 NoSuchAlgorithmException: MD4 for provider BC。
     */
    private fun installNtlmCryptoProvider() {
        try {
            val bc = BouncyCastleProvider()
            runCatching { Crypto.initProvider(bc) }
                .onFailure { Log.w(TAG, "jcifs provider 已初始化，跳过注入", it) }
            if (Security.getProvider(bc.name) !== bc) {
                Security.removeProvider(bc.name)
                Security.addProvider(bc)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "初始化 BC 提供者失败", t)
        }
    }

    private companion object {
        const val TAG = "NovelReader"
    }
}
