package xyz.gnarbot.gnar.commands.executors.mod;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.utils.PermissionUtil;
import xyz.gnarbot.gnar.commands.handlers.Command;
import xyz.gnarbot.gnar.commands.handlers.CommandExecutor;
import xyz.gnarbot.gnar.members.BotPermission;
import xyz.gnarbot.gnar.members.Person;
import xyz.gnarbot.gnar.servers.Host;
import xyz.gnarbot.gnar.utils.Note;

import java.util.List;

@Command(aliases = "ban", botPermission = BotPermission.BOT_COMMANDER)
public class BanCommand extends CommandExecutor {
    @Override
    public void execute(Note note, List<String> args) {
        Host host = note.getHost();

        Person author = note.getAuthor();
        Person target = null;

        if (!PermissionUtil.checkPermission(note.getTextChannel(), author, Permission.BAN_MEMBERS)) {
            note.error("You do not have permission to ban.");
            return;
        }

        if (note.getMentionedChannels().size() >= 1) {
            target = note.getMentionedUsers().get(0);
        } else if (args.size() >= 1) {
            target = note.getHost().getPersonHandler().getUser(args.get(0));
        }

        if (target == null) {
            note.error("Could not find user.");
            return;
        }
        if (!PermissionUtil.canInteract(author, target)) {
            note.error("Sorry, that user has an equal or higher role.");
            return;
        }

        if (!host.ban(target)) {
            note.error("Gnar does not have permission to ban.");
            return;
        }
        note.info(target.getEffectiveName() + " has been banned.");
    }
}