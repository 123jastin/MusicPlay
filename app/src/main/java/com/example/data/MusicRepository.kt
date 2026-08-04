package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class MusicRepository(private val musicDao: MusicDao) {

    val allTracks: Flow<List<MusicTrack>> = musicDao.getAllTracks()
    val allQueues: Flow<List<PlaybackQueue>> = musicDao.getAllQueues()
    val allPlaylists: Flow<List<Playlist>> = musicDao.getAllPlaylists()

    suspend fun getTrackById(id: Int): MusicTrack? = musicDao.getTrackById(id)

    suspend fun insertTrack(track: MusicTrack): Long = musicDao.insertTrack(track)

    suspend fun updateTrack(track: MusicTrack) = musicDao.updateTrack(track)

    suspend fun deleteTrack(track: MusicTrack) = musicDao.deleteTrack(track)

    suspend fun getQueueById(queueId: Int): PlaybackQueue? = musicDao.getQueueById(queueId)

    suspend fun insertQueue(queue: PlaybackQueue) = musicDao.insertQueue(queue)

    suspend fun updateQueue(queue: PlaybackQueue) = musicDao.updateQueue(queue)

    suspend fun deleteQueue(queueId: Int) = musicDao.deleteQueue(queueId)

    suspend fun getPlaylistById(playlistId: Int): Playlist? = musicDao.getPlaylistById(playlistId)

    suspend fun insertPlaylist(playlist: Playlist): Long = musicDao.insertPlaylist(playlist)

    suspend fun updatePlaylist(playlist: Playlist) = musicDao.updatePlaylist(playlist)

    suspend fun deletePlaylist(playlist: Playlist) = musicDao.deletePlaylist(playlist)

    suspend fun prepopulateIfEmpty() {
        val currentTracks = allTracks.first()
        if (currentTracks.isEmpty()) {
            val defaultTracks = listOf(
                MusicTrack(
                    title = "Golden Horizons",
                    artist = "Solar Flare",
                    album = "Cosmic Dreams",
                    genre = "Ambient",
                    year = "2024",
                    trackNumber = "1",
                    durationSec = 145,
                    lyrics = "Through the solar winds we glide,\nNo shadows here to hide.\nGolden rays of celestial light,\nGuide our ship across the night..\n\nSearching for the cosmic dawn,\nBefore the morning stars are gone.",
                    folder = "Space Chill"
                ),
                MusicTrack(
                    title = "Neon Nocturne",
                    artist = "Retro Synth",
                    album = "Cyberpunk Dreamscapes",
                    genre = "Synthwave",
                    year = "2025",
                    trackNumber = "2",
                    durationSec = 172,
                    lyrics = "City rain on metal streets,\nBouncing to simulated beats.\nHolograms of glowing blue,\nWhispering of a past we knew..\n\nNeon eyes and silicon hearts,\nWhere the cybernetic journey starts.",
                    folder = "Synth Wave"
                ),
                MusicTrack(
                    title = "Starlight Sonata",
                    artist = "Celeste",
                    album = "Classical Reimagined",
                    genre = "Classical",
                    year = "2023",
                    trackNumber = "3",
                    durationSec = 194,
                    lyrics = "A starlit sky, so vast, so deep,\nWhile the quiet meadows sleep.\nNotes of piano gently fall,\nAnswering the lonely galaxy's call.\n\nTime stands still in this cosmic song,\nWhere we feel we both belong.",
                    folder = "Classical Ambient"
                ),
                MusicTrack(
                    title = "Breeze of Kyoto",
                    artist = "Zen Whispers",
                    album = "Traditional Chill",
                    genre = "Chillout",
                    year = "2026",
                    trackNumber = "4",
                    durationSec = 155,
                    lyrics = "Bamboo leaves in gentle wind,\nLeaves behind the thoughts we pinned.\nIn the silence of the shrine,\nPeace and harmony align.\n\nWater flows, a peaceful stream,\nFading into a summer dream.",
                    folder = "Zen Garden"
                ),
                MusicTrack(
                    title = "Digital Rain",
                    artist = "Code Beats",
                    album = "Lofi Coding Hooks",
                    genre = "Lofi Hip-Hop",
                    year = "2024",
                    trackNumber = "5",
                    durationSec = 138,
                    lyrics = "Lines of code scroll down the screen,\nIn the quietest hour we've ever seen.\nRaindrops tapping on the glass,\nWaiting for the night to pass.\n\nCompile, run, and take a sip,\nA quiet lofi keyboard trip.",
                    folder = "Coding Beats"
                ),
                MusicTrack(
                    title = "Midnight Expressway",
                    artist = "Nightdrive",
                    album = "Synthesized Highways",
                    genre = "Outrun",
                    year = "2025",
                    trackNumber = "6",
                    durationSec = 165,
                    lyrics = "Eighty-eight miles and counting fast,\nLeaving all our fears in the past.\nDashboard glowing, radio high,\nDriving straight into the crimson sky.\n\nFeel the engine, hear the sound,\nOn this highway, we are bound.",
                    folder = "Synth Wave"
                ),
                MusicTrack(
                    title = "Morning Espresso",
                    artist = "Caffeine",
                    album = "Acoustic Folk Café",
                    genre = "Acoustic",
                    year = "2023",
                    trackNumber = "7",
                    durationSec = 142,
                    lyrics = "Warm cup sitting in my hand,\nSteaming like a sleepy land.\nSunlight creeping through the blind,\nLeaving all the stress behind.\n\nGuitar strings and coffee sweet,\nWalking on a dynamic street.",
                    folder = "Acoustic Cafe"
                ),
                MusicTrack(
                    title = "Ocean Waves",
                    artist = "Deep Blue",
                    album = "Nature Soundscapes",
                    genre = "Ambient / Nature",
                    year = "2026",
                    trackNumber = "8",
                    durationSec = 180,
                    lyrics = "[Instrumental - Natural Ocean Wave Frequencies with Soft Floating Pad Synths for Sleep and Deep Relaxation]",
                    folder = "Nature Chill"
                ),
                MusicTrack(
                    title = "Sub-Zero Beat",
                    artist = "Frostbite",
                    album = "Glitch Hop Glacier",
                    genre = "Electronic",
                    year = "2024",
                    trackNumber = "9",
                    durationSec = 148,
                    lyrics = "Frozen bass lines, icy claps,\nGlitching on your winter maps.\nSub-zero pulses in the snow,\nHearing frosty whispers grow.\n\nDance under the Northern sky,\nAs the crystal beats fly high.",
                    folder = "Glacier Beats"
                ),
                MusicTrack(
                    title = "Emerald Canopy",
                    artist = "Jungle Pulse",
                    album = "Organic Deep House",
                    genre = "Deep House",
                    year = "2025",
                    trackNumber = "10",
                    durationSec = 161,
                    lyrics = "[Vocal Chop: Welcome to the forest deep, where the ancient rhythms sleep]\nGreen leaves dancing to the beat,\nTropical drums under our feet.\nDeep house grooves under the shade,\nIn the canopy nature made.",
                    folder = "Jungle Beats"
                )
            )
            musicDao.insertTracks(defaultTracks)
        }

        // Initialize queues. Musicolet excels at multi-queues. Let's create 4 default queues in Room!
        val currentQueues = allQueues.first()
        if (currentQueues.isEmpty()) {
            val seededTracks = allTracks.first()
            val trackIds = seededTracks.map { it.id }

            // Split tracks across 3 initial queues and keep 1 empty queue
            val q1Ids = trackIds.take(4).joinToString(",")
            val q2Ids = trackIds.drop(4).take(3).joinToString(",")
            val q3Ids = trackIds.drop(7).joinToString(",")

            musicDao.insertQueue(PlaybackQueue(queueId = 1, name = "Primary Queue", trackIdsString = q1Ids, currentIndex = 0, currentPositionMs = 0L))
            musicDao.insertQueue(PlaybackQueue(queueId = 2, name = "Chill Sessions", trackIdsString = q2Ids, currentIndex = 0, currentPositionMs = 0L))
            musicDao.insertQueue(PlaybackQueue(queueId = 3, name = "Cosmic Night", trackIdsString = q3Ids, currentIndex = 0, currentPositionMs = 0L))
            musicDao.insertQueue(PlaybackQueue(queueId = 4, name = "Empty Workbench", trackIdsString = "", currentIndex = 0, currentPositionMs = 0L))
        }

        // Initialize playlists. Let's create some default playlists!
        val currentPlaylists = allPlaylists.first()
        if (currentPlaylists.isEmpty()) {
            val seededTracks = allTracks.first()
            val synthTracksIds = seededTracks.filter { it.genre == "Synthwave" || it.genre == "Electronic" }.map { it.id }.joinToString(",")
            val ambientTracksIds = seededTracks.filter { it.genre == "Ambient" || it.genre == "Classical" || it.genre == "Chillout" }.map { it.id }.joinToString(",")

            musicDao.insertPlaylist(Playlist(name = "Midnight Drivers", description = "Synthwave, techno, and futuristic chillout sequences.", trackIdsString = synthTracksIds))
            musicDao.insertPlaylist(Playlist(name = "Zen Sanctuary", description = "Peaceful harmonies and ambient melodies to study, rest, and meditate.", trackIdsString = ambientTracksIds))
        }
    }
}
