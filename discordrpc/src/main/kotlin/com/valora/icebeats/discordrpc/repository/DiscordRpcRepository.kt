/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * DiscordRpcRepositoryImpl.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.valora.icebeats.discordrpc.repository

import com.valora.icebeats.discordrpc.remote.ApiService
import com.valora.icebeats.discordrpc.utils.toImageAsset

/**
 * Modified by Zion Huang
 */
class DiscordRpcRepository {
    private val api = ApiService()

    suspend fun getImage(url: String): String? {
        return api.getImage(url).getOrNull()?.toImageAsset()
    }
}
