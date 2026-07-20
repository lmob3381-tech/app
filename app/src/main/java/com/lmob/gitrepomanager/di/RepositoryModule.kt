package com.lmob.gitrepomanager.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * GitHubRepository currently uses constructor injection (@Inject constructor)
 * so no explicit @Binds/@Provides are required here. This module is kept as
 * an extension point for Step 2, e.g. if repository interfaces are
 * introduced for testability (GitHubRepository -> IGitHubRepository).
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule
