/*
 * BioPay - biometric payment assistance for supported payment apps.
 * Copyright (C) 2026 kiriashi
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.kiriashi.biopay.payment

import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import io.github.kiriashi.biopay.core.log.ModuleLog

/** Includes Activity, Dialog and popup roots without reading framework internals. */
internal object PaymentWindowRoots {
    /**
     * Several observers ask for the window list within the same frame. Enumerating global
     * windows is a reflective walk, so reuse one snapshot for a very short window. The
     * cache only ever lives on the main thread and expires fast enough to stay correct.
     */
    private const val CACHE_TTL_MS = 50L

    @Volatile private var cachedAt = 0L
    @Volatile private var cached: List<ViewGroup> = emptyList()

    fun attached(): List<ViewGroup> {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Window inspection must run on the main thread" }
        val now = SystemClock.uptimeMillis()
        val snapshot = cached
        if (now - cachedAt < CACHE_TTL_MS && cachedAt != 0L) return snapshot
        val fresh = runCatching {
            WindowInspector.getGlobalWindowViews().mapNotNull { view ->
                (view as? ViewGroup)?.takeIf { it.isAttachedToWindow }
            }
        }.onFailure { ModuleLog.w(it) { "payment window enumeration failed" } }
            .getOrDefault(emptyList())
        // Keep the last known windows when a transient enumeration returns nothing.
        if (fresh.isNotEmpty() || snapshot.isEmpty()) {
            cached = fresh
            cachedAt = now
        }
        return fresh
    }
}
