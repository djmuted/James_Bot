package me.skypaw.jamesBot;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class VoiceMessages extends ListenerAdapter {

    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;

    private Thread t = null;

    private final ArrayList<String> soundsToCommand;
    private final ArrayList<String> randomSounds;

    private final List<String> randomSoundsName;
    final List<String> soundsToCommandName;


    private String randomDirectory = "random";
    private String soundsDirectory = "sounds";


    VoiceMessages() throws IOException {
        //Listing files on the start
        this.soundsToCommand = listFiles(soundsDirectory);
        this.randomSounds = listFiles(randomDirectory);

        //Dividing directory strings
        this.randomSoundsName = stringDirectorySeparator(randomSounds);
        this.soundsToCommandName = stringDirectorySeparator(soundsToCommand);


        this.musicManagers = new HashMap<>();
        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerLocalSource(playerManager);

    }

    private List<String> stringDirectorySeparator(ArrayList<String> list) {
        String separator = "\\\\";
        String separator1 = "\\.";
        List<String> name = new ArrayList<>();

        for (String s : list) {
            String[] parts = s.toLowerCase().split(separator);
            String[] parts2 = parts[1].split(separator1);

            name.add(parts2[0]);
        }

        return name;
    }

    private ArrayList<String> listFiles(String directory) throws IOException {

        ArrayList<String> list = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(Paths.get(directory))) {
            paths
                    .filter(Files::isRegularFile)
                    .forEach((temp) -> list.add(temp.toString()));
        }

        return list;
    }

    private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
        long guildId = Long.parseLong(guild.getId());
        GuildMusicManager musicManager = musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager);
            musicManagers.put(guildId, musicManager);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }

    @Override
    public void onGuildVoiceJoin(GuildVoiceJoinEvent event) {
        if (!event.getChannelJoined().getName().equals("Pokój")) return;
        if (event.getMember().getUser().getName().equals("Hydra") || event.getMember().getUser().getName().equals("JamesBot"))
            return;

        String hello = "sounds/hi.m4a";

        try {
            Thread.sleep(600);
            loadAndPlay(event.getChannelJoined(), hello);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onGuildVoiceLeave(GuildVoiceLeaveEvent event) {

        if (event.getChannelLeft().getName().equals("Pokój") && event.getChannelLeft().getMembers().size() == 1) {
            event.getGuild().getAudioManager().closeAudioConnection();
        } else if (!event.getChannelLeft().getName().equals("Pokój") && event.getGuild().getVoiceChannelsByName("Pokój", true).get(0).getMembers().size() == 1) {
            event.getGuild().getAudioManager().closeAudioConnection();
        }

    }


    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {

        if (!event.getChannel().getName().equals("the-grand-tour")) return;
        if (event.getAuthor().getName().equals("JamesBot")) return;
        if (event.getMessage().getContentDisplay().equals("random")) {
            randomThread(event);
        }
        if(event.getMessage().getContentDisplay().equals("stoprandom")){
            stopRandomThread();
        }

                String[] command = event.getMessage().getContentRaw().split(" ", 2);
        int i = 0;
        for (String s : soundsToCommandName) {
            if (s.equals(command[0].toLowerCase())) {
                loadAndPlay(event.getChannel(), soundsToCommand.get(i));
            }
            i++;
        }

        super.onGuildMessageReceived(event);
    }

    private void loadAndPlay(final GuildChannel channel, final String trackUrl) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {

            @Override
            public void trackLoaded(AudioTrack track) {
                play(channel.getGuild(), musicManager, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack firstTrack = playlist.getSelectedTrack();

                if (firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }

                play(channel.getGuild(), musicManager, firstTrack);
            }

            @Override
            public void noMatches() {
            }

            @Override
            public void loadFailed(FriendlyException exception) {
            }
        });
    }

    private void play(Guild guild, GuildMusicManager musicManager, AudioTrack track) {
        connectToFirstVoiceChannel(guild.getAudioManager());

        musicManager.scheduler.queue(track);
    }

    private static void connectToFirstVoiceChannel(AudioManager audioManager) {
        if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
            for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannelsByName("Pokój", true)) {
                audioManager.openAudioConnection(voiceChannel);
                break;
            }
        }
    }


    /* RANDOM
     * Methods responsible for playing random sounds from dictionary "random"
     * Sounds will be waiting to queue in new thread, so the main functionality will be available.
     */

    public int randomPlayer(GuildChannel voiceChannel) {
        Random test = new Random();

        int randomSound = test.nextInt(randomSounds.size());
        String randomSoundString = String.valueOf(randomSound);

        int i = 0;
        for (String s : randomSoundsName) {
            if (s.equals(randomSoundString)) {
                loadAndPlay(voiceChannel, randomSounds.get(i));
            }
            i++;
        }

        int min = 60 * 1000 * 10;
        int randomTime = test.nextInt(25) * 1000 * 60 + min;

        System.out.println(randomTime / 60 / 1000);

        return randomTime;

    }


    private void randomThread(GuildMessageReceivedEvent event) {

        //Infinite loop to adding to play queue random sounds, and sleeping the thread for specific pseudo random time.
        Runnable runnable = () -> {
            while (true) {
                try {
                    int timeThread = randomPlayer(event.getChannel());

                    Thread.sleep(timeThread);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };

        //Statement to avoid creating many threads with random sounds
        if (t == null) {
            t = new Thread(runnable);
            t.start();
        }

    }

    private void stopRandomThread() {

        //Stopping the thread, and changing t to null so it can be launch again
        if (t != null) {
            t.stop();
            t = null;
        }
    }
}

