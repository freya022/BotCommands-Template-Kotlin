package io.github.freya022.bot

import dev.minn.jda.ktx.events.CoroutineEventManager
import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorRuntime
import io.github.freya022.bot.config.Config
import io.github.freya022.bot.config.Environment
import io.github.freya022.botcommands.api.core.BotCommands
import io.github.freya022.botcommands.api.core.config.DevConfig
import io.github.freya022.botcommands.api.core.utils.namedDefaultScope
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.interactions.DiscordLocale
import java.lang.management.ManagementFactory
import kotlin.io.path.absolutePathString
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.minutes
import ch.qos.logback.classic.ClassicConstants as LogbackConstants

private val logger by lazy { KotlinLogging.logger {} } // Must not load before system property is set

private const val mainPackageName = "io.github.freya022.bot"
private const val botName = "BotTemplate"

object Main {
    @JvmStatic
    fun main(args: Array<out String>) {
        try {
            System.setProperty(LogbackConstants.CONFIG_FILE_PROPERTY, Environment.logbackConfigPath.absolutePathString())
            logger.info { "Loading logback configuration at ${Environment.logbackConfigPath.absolutePathString()}" }

            // I use hotswap agent to update my code without restarting the bot
            // Of course this only supports modifying existing code
            // Refer to https://github.com/HotswapProjects/HotswapAgent#readme on how to use hotswap

            // stacktrace-decoroutinator has issues when reloading with hotswap agent
            if ("-XX:+AllowEnhancedClassRedefinition" in ManagementFactory.getRuntimeMXBean().inputArguments) {
                logger.info { "Skipping stacktrace-decoroutinator as enhanced hotswap is active" }
            } else if ("--no-decoroutinator" in args) {
                logger.info { "Skipping stacktrace-decoroutinator as --no-decoroutinator is specified" }
            } else {
                DecoroutinatorRuntime.load()
            }

            // Create a scope for our event manager
            val scope = namedDefaultScope("$botName Coroutine", 4)
            val manager = CoroutineEventManager(scope, 1.minutes)

            val config = Config.instance

            BotCommands.create(manager) {
                if (Environment.isDev) {
                    disableExceptionsInDMs = true
                    @OptIn(DevConfig::class)
                    disableAutocompleteCache = true
                }

                addOwners(config.ownerIds)

                addSearchPath(mainPackageName)

                textCommands {
                    //Use ping as prefix if configured
                    usePingAsPrefix = "<ping>" in config.prefixes
                    prefixes += config.prefixes - "<ping>"
                }

                applicationCommands {
                    // Check command updates based on Discord's commands.
                    // This is only useful during development,
                    // as you can develop on multiple machines (but not simultaneously!).
                    // Using this in production is only going to waste API requests.
                    @OptIn(DevConfig::class)
                    onlineAppCommandCheckEnabled = Environment.isDev

                    // Guilds in which `@Test` commands will be inserted
                    testGuildIds += config.testGuildIds

                    // Add french localization for application commands
                    addLocalizations("Commands", DiscordLocale.FRENCH)
                }

                components {
                    // Enables usage of components
                    useComponents = true
                }
            }

            // There is no JDABuilder going on here, it's taken care of in Bot

            logger.info { "Loaded bot" }
        } catch (e: Exception) {
            logger.error(e) { "Unable to start the bot" }
            exitProcess(1)
        }
    }
}
