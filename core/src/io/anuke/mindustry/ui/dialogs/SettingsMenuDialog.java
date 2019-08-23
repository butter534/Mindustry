package io.anuke.mindustry.ui.dialogs;

import io.anuke.arc.*;
import io.anuke.arc.collection.*;
import io.anuke.arc.files.*;
import io.anuke.arc.graphics.*;
import io.anuke.arc.graphics.Texture.*;
import io.anuke.arc.input.*;
import io.anuke.arc.scene.*;
import io.anuke.arc.scene.event.*;
import io.anuke.arc.scene.ui.*;
import io.anuke.arc.scene.ui.SettingsDialog.SettingsTable.*;
import io.anuke.arc.scene.ui.layout.*;
import io.anuke.arc.util.*;
import io.anuke.mindustry.core.GameState.*;
import io.anuke.mindustry.core.*;
import io.anuke.mindustry.gen.*;
import io.anuke.mindustry.graphics.*;
import io.anuke.mindustry.net.Net;

import static io.anuke.arc.Core.bundle;
import static io.anuke.mindustry.Vars.*;

public class SettingsMenuDialog extends SettingsDialog{
    public SettingsTable graphics;
    public SettingsTable game;
    public SettingsTable sound;

    private Table prefs;
    private Table menu;
    private boolean wasPaused;

    public SettingsMenuDialog(){
        setStyle(Core.scene.skin.get("dialog", WindowStyle.class));

        hidden(() -> {
            Sounds.back.play();
            if(!state.is(State.menu)){
                if(!wasPaused || Net.active())
                    state.set(State.playing);
            }
        });

        shown(() -> {
            back();
            if(!state.is(State.menu)){
                wasPaused = state.is(State.paused);
                state.set(State.paused);
            }

            rebuildMenu();
        });

        setFillParent(true);
        title.setAlignment(Align.center);
        titleTable.row();
        titleTable.add(new Image("whiteui")).growX().height(3f).pad(4f).get().setColor(Pal.accent);

        cont.clearChildren();
        cont.remove();
        buttons.remove();

        menu = new Table("button");

        game = new SettingsTable();
        graphics = new SettingsTable();
        sound = new SettingsTable();

        prefs = new Table();
        prefs.top();
        prefs.margin(14f);

        rebuildMenu();

        prefs.clearChildren();
        prefs.add(menu);

        ScrollPane pane = new ScrollPane(prefs);
        pane.addCaptureListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                Element actor = pane.hit(x, y, true);
                if(actor instanceof Slider){
                    pane.setFlickScroll(false);
                    return true;
                }

                return super.touchDown(event, x, y, pointer, button);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                pane.setFlickScroll(true);
                super.touchUp(event, x, y, pointer, button);
            }
        });
        pane.setFadeScrollBars(false);

        row();
        add(pane).grow().top();
        row();
        add(buttons).fillX();

        addSettings();
    }

    void rebuildMenu(){
        menu.clearChildren();

        String style = "clear";

        menu.defaults().size(300f, 60f);
        menu.addButton("$settings.game", style, () -> visible(0));
        menu.row();
        menu.addButton("$settings.graphics", style, () -> visible(1));
        menu.row();
        menu.addButton("$settings.sound", style, () -> visible(2));
        menu.row();
        menu.addButton("$settings.language", style, ui.language::show);
        if(!mobile || Core.settings.getBool("keyboard")){
            menu.row();
            menu.addButton("$settings.controls", style, ui.controls::show);
        }
    }

    void addSettings(){
        sound.sliderPref("musicvol", bundle.get("setting.musicvol.name", "Music Volume"), 100, 0, 100, 1, i -> i + "%");
        sound.sliderPref("sfxvol", bundle.get("setting.sfxvol.name", "SFX Volume"), 100, 0, 100, 1, i -> i + "%");
        sound.sliderPref("ambientvol", bundle.get("setting.ambientvol.name", "Ambient Volume"), 100, 0, 100, 1, i -> i + "%");

        game.screenshakePref();
        if(mobile){
            game.checkPref("autotarget", true);
            game.checkPref("keyboard", false);
        }
        game.sliderPref("saveinterval", 60, 10, 5 * 120, i -> Core.bundle.format("setting.seconds", i));

        if(!mobile){
            game.checkPref("crashreport", true);
        }

        game.pref(new Setting(){
            @Override
            public void add(SettingsTable table){
                table.addButton("$settings.cleardata", () -> ui.showConfirm("$confirm", "$settings.clearall.confirm", () -> {
                    ObjectMap<String, Object> map = new ObjectMap<>();
                    for(String value : Core.settings.keys()){
                        if(value.contains("usid") || value.contains("uuid")){
                            map.put(value, Core.settings.getString(value));
                        }
                    }
                    Core.settings.clear();
                    Core.settings.putAll(map);
                    Core.settings.save();

                    for(FileHandle file : dataDirectory.list()){
                        file.deleteDirectory();
                    }

                    Core.app.exit();
                })).size(220f, 60f).pad(6).left();
                table.add();
                table.row();
            }
        });

        game.pref(new Setting(){
            @Override
            public void add(SettingsTable table){
                table.addButton("$tutorial.retake", () -> {
                    hide();
                    control.playTutorial();
                }).size(220f, 60f).pad(6).left();
                table.add();
                table.row();
                hide();
            }
        });

        if(android && (Core.files.local("mindustry-maps").exists() || Core.files.local("mindustry-saves").exists())){
            game.pref(new Setting(){
                @Override
                public void add(SettingsTable table){
                    table.addButton("$classic.export", () -> {
                        control.checkClassicData();
                    }).size(220f, 60f).pad(6).left();
                    table.add();
                    table.row();
                    hide();
                }
            });
        }

        graphics.sliderPref("uiscale", 100, 25, 400, 25, s -> {
            if(Core.graphics.getFrameId() > 10){
                Log.info("changed");
                Core.settings.put("uiscalechanged", true);
            }
            return s + "%";
        });
        graphics.sliderPref("fpscap", 241, 5, 241, 5, s -> (s > 240 ? Core.bundle.get("setting.fpscap.none") : Core.bundle.format("setting.fpscap.text", s)));
        graphics.sliderPref("chatopacity", 100, 0, 100, 5, s -> s + "%");

        if(!mobile){
            graphics.checkPref("vsync", true, b -> Core.graphics.setVSync(b));
            graphics.checkPref("fullscreen", false, b -> {
                if(b){
                    Core.graphics.setFullscreenMode(Core.graphics.getDisplayMode());
                }else{
                    Core.graphics.setWindowedMode(600, 480);
                }
            });

            graphics.checkPref("borderlesswindow", false, b -> Core.graphics.setUndecorated(b));

            Core.graphics.setVSync(Core.settings.getBool("vsync"));
            if(Core.settings.getBool("fullscreen")){
                Core.app.post(() -> Core.graphics.setFullscreenMode(Core.graphics.getDisplayMode()));
            }

            if(Core.settings.getBool("borderlesswindow")){
                Core.app.post(() -> Core.graphics.setUndecorated(true));
            }
        }else{
            graphics.checkPref("landscape", false, b -> {
                if(b){
                    Platform.instance.beginForceLandscape();
                }else{
                    Platform.instance.endForceLandscape();
                }
            });

            if(Core.settings.getBool("landscape")){
                Platform.instance.beginForceLandscape();
            }
        }

        graphics.checkPref("effects", true);
        graphics.checkPref("playerchat", true);
        graphics.checkPref("minimap", !mobile);
        graphics.checkPref("fps", false);
        graphics.checkPref("indicators", true);
        graphics.checkPref("animatedwater", false);
        if(Shaders.shield != null){
            graphics.checkPref("animatedshields", !mobile);
        }
        graphics.checkPref("bloom", false, val -> renderer.toggleBloom(val));
        graphics.checkPref("lasers", true);
        graphics.checkPref("pixelate", false);

        graphics.checkPref("linear", false, b -> {
            for(Texture tex : Core.atlas.getTextures()){
                TextureFilter filter = b ? TextureFilter.Linear : TextureFilter.Nearest;
                tex.setFilter(filter, filter);
            }
        });

        if(Core.settings.getBool("linear")){
            for(Texture tex : Core.atlas.getTextures()){
                TextureFilter filter = TextureFilter.Linear;
                tex.setFilter(filter, filter);
            }
        }
    }

    private void back(){
        rebuildMenu();
        prefs.clearChildren();
        prefs.add(menu);
    }

    private void visible(int index){
        prefs.clearChildren();
        prefs.add(new Table[]{game, graphics, sound}[index]);
    }

    @Override
    public void addCloseButton(){
        buttons.addImageTextButton("$back", "icon-arrow-left", 30f, () -> {
            if(prefs.getChildren().first() != menu){
                back();
            }else{
                hide();
            }
        }).size(230f, 64f);

        keyDown(key -> {
            if(key == KeyCode.ESCAPE || key == KeyCode.BACK){
                hide();
            }
        });
    }
}
