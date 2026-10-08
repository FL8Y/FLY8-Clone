package com.fly8.clone.backend

import com.fly8.clone.model.CloneEntry

interface CloneBackend {
    fun create(packageName: String, label: String): Result<CloneEntry>
    fun launch(clone: CloneEntry): Result<Unit>
    fun stop(clone: CloneEntry): Result<Unit>
    fun clear(clone: CloneEntry): Result<Unit>
    fun remove(clone: CloneEntry): Result<Unit>
}
