package net.runelite.client

import com.google.inject.Provides
import net.runelite.api.*
import net.runelite.api.events.GameTick
import net.runelite.api.events.MenuOptionClicked
import net.runelite.api.events.NpcSpawned
import net.runelite.api.events.WidgetClosed
import net.runelite.api.events.WidgetPressed
import net.runelite.api.widgets.WidgetInfo
import net.runelite.client.config.ConfigManager
import net.runelite.client.eventbus.Subscribe
import net.runelite.client.events.ConfigChanged
import net.runelite.client.plugins.Plugin
import net.runelite.client.plugins.PluginDescriptor
import net.runelite.client.plugins.oneclickconstruction.States
import net.runelite.client.plugins.oneclickconstruction.util.Log
import net.runelite.client.plugins.oneclickconstruction.OneClickConstructionConfig
import net.runelite.client.plugins.oneclickconstruction.OneClickConstructionConfig.Constructables
import net.runelite.client.plugins.oneclickconstruction.OneClickConstructionConfig.Constructables.*
import net.runelite.client.plugins.oneclickconstruction.api.entry.Entries
import net.runelite.client.plugins.oneclickconstruction.api.inventory.Inventory
import net.runelite.client.plugins.oneclickconstruction.client.findGameObject
import net.runelite.client.plugins.oneclickconstruction.client.findNpc
import net.runelite.client.plugins.oneclickconstruction.client.findWallObject
import net.runelite.client.plugins.oneclickwintertodt.magic.BUTLERS
import org.pf4j.Extension
import javax.inject.Inject
import kotlin.properties.Delegates

@Extension
@PluginDescriptor(
    name = "One Click Construction",
    description = ":Prayje:",
    tags = ["rebecca, oneclick, one click, construction"]
)
class OneClickConstructionPlugin : Plugin() {

    @Inject
    private lateinit var config: OneClickConstructionConfig

    @Inject
    lateinit var client: Client

    @Inject
    lateinit var entries: Entries

    @Inject
    lateinit var inventories: Inventory

    companion object : Log()

    @Provides
    fun provideConfig(configManager: ConfigManager): OneClickConstructionConfig {
        return configManager.getConfig(OneClickConstructionConfig::class.java)
    }

    lateinit var method: Constructables
    private var buildable: TileObject? = null
    private var built: TileObject? = null
    private var inUse = false
    private var butler: NPC? = null
    private var performAction = true
    private var timeout = 0

    override fun startUp() {
        log.info("Starting One Click Construction")
        reset()
    }

    override fun shutDown() {
        log.info("Stopping One Click Construction")
    }

    private fun reset() {
        method = config.method()
        inUse = false
        performAction = true
        timeout = 0
    }

    private var state by Delegates.observable(States.IDLE) { property, previous, current ->
        if (previous != current) {
            performAction = true
        }
    }

    @Subscribe
    private fun onConfigChanged(event: ConfigChanged) {
        method = config.method()
    }

    @Subscribe
    private fun onGameTick(event: GameTick) {
        if(timeout > 0) {
            timeout--
        }
        butler = client.findNpc(BUTLERS)
        when(config.method()) {
            MAHOGANY_TABLE -> {
                buildable = client.findGameObject(method.buildable)
                built = client.findGameObject(method.built)
            }
            OAK_DOOR -> {
                buildable = client.findWallObject(method.buildable)
                built = client.findWallObject(method.built)
            }
            OAK_LARDER -> {
                buildable = client.findGameObject(method.buildable)
                built = client.findGameObject(method.built)
            }
        }
    }

    @Subscribe
    fun onNpcSpawned(event: NpcSpawned) {
        if(state != States.CALL_BUTLER && BUTLERS.contains(event.npc.id)) {
            if(inUse) {
                inUse = false
                return
            }
        }
    }

    @Subscribe
    fun onMenuEntryClicked(event: MenuOptionClicked) {
        handleLogic()
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "$state $performAction $inUse", "")
        if(!performAction || timeout != 0) {
            event.consume()
            return
        }
        performAction = false
        with(entries) {
            when (state) {
                States.BUILD -> {
                    buildable?.let {
                        event.interact(it)
                        return
                    }
                }
                States.REMOVE -> {
                    built?.let {
                        event.interact(it)
                        return
                    }
                }
                States.PRESS_BUILD -> {
                    event.click(-1, client.getWidget(458, config.method().childId)?.id!!)
                    return
                }
                States.CONFIRM_REMOVE -> {
                    event.talk(1, 14352385)
                    return
                }
                States.CALL_BUTLER -> {
                    if(client.getWidget(24248339) == null) {
                        performAction = true
                        event.click(-1, 7602250)
                        return
                    }
                    event.click(-1, 24248339)
                    return
                }
                States.USE_BUTLER -> {
                    if(client.getWidget(14352385) == null) {
                        butler?.let {
                            performAction = true
                            event.talkTo(butler!!, MenuAction.NPC_FIRST_OPTION)
                            return
                        }
                    }
                    performAction = true
                    inUse = true
                    event.talk(1, 14352385)
                    return
                }
                else -> {}
            }
        }
    }
    private fun handleLogic() {
        with(inventories) {
            if (!inUse && (butler == null || butler?.worldLocation?.distanceTo(client.localPlayer.worldLocation)!! > 1)) {
                state = States.CALL_BUTLER
                return
            }
            if (!inUse && WidgetInfo.INVENTORY.quantity(method.plank) < 26 && butler?.worldLocation?.distanceTo(client.localPlayer.worldLocation)!! <= 1) {
                state = States.USE_BUTLER
                return
            }
            if(inUse && butler != null) {
                return
            }
            if (buildable != null && WidgetInfo.INVENTORY.quantity(method.plank) >= method.amount) {
                if (client.getWidget(30015488) != null) {
                    state = States.PRESS_BUILD
                    return
                }
                state = States.BUILD
                return
            }
            if (built != null) {
                if (client.getWidget(14352385)?.getChild(1)?.text == "Yes") {
                    state = States.CONFIRM_REMOVE
                    return
                }
                state = States.REMOVE
                return
            }
            state = States.IDLE
            return
        }
    }
}