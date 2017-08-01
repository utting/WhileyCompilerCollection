package wycc;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Map;

import wybs.lang.SyntaxError;
import wycc.commands.Help;
import wycc.lang.Command;
import wycc.lang.ConfigFile;
import wycc.lang.Feature.ConfigurationError;
import wycc.lang.Module;
import wycc.util.Pair;
import wyfs.lang.Content;
import wyfs.lang.Path;
import wyfs.util.DirectoryRoot;
import wyfs.util.Trie;

/**
 * Provides a command-line interface to the Whiley Compiler Collection. This
 * supports loading and configuring modules, as well as compiling files.
 *
 * @author David J. Pearce
 *
 */
public class WyMain {
	/**
	 * Default implementation of a content registry. This associates whiley and
	 * wyil files with their respective content types.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Registry implements Content.Registry {
		@Override
		public void associate(Path.Entry e) {
			String suffix = e.suffix();

			if (suffix.equals("wyml")) {
				e.associate(ConfigFile.ContentType, null);
			}
		}

		@Override
		public String suffix(Content.Type<?> t) {
			return t.getSuffix();
		}
	}

	// ==================================================================
	// Main Method
	// ==================================================================

	public static void main(String[] args) throws IOException {
		String whileyhome = System.getenv("WHILEYHOME");
		if(whileyhome == null) {
			System.err.println("error: WHILEYHOME environment variable not set");
			System.exit(-1);
		}
		WyTool tool = constructWyTool(whileyhome);
		// process command-line options
		Command command = null;
		ArrayList<String> commandArgs = new ArrayList<>();

		// Parse command-line options and determine the command to execute
		boolean success = true;
		//
		for(int i=0;i!=args.length;++i) {
			String arg = args[i];
			//
			if (arg.startsWith("--")) {
				Pair<String,Object> option = parseOption(arg);
				success &= applyOption(option,tool,command);
			} else if(command == null) {
				command = tool.getCommand(arg);
			} else {
				commandArgs.add(arg);
			}
		}

		// Execute the command (if applicable)
		if (command == null) {
			// Not applicable, print usage information via the help sub-system.
			tool.getCommand("help").execute();
		} else if(!success) {
			// There was some problem during configuration
			System.exit(1);
		} else {
			// Yes, execute the given command
			args = commandArgs.toArray(new String[commandArgs.size()]);
			command.execute(args);
			System.exit(0);
		}
	}

	// ==================================================================
	// Helpers
	// ==================================================================

	private static WyTool constructWyTool(String whileyhome) throws IOException {
		WyTool tool = new WyTool();
		Registry registry = new Registry();
		// Register default commands
		registerDefaultCommands(tool);
		// Attempt to read global configuration
		DirectoryRoot globalConfigDir = new DirectoryRoot(whileyhome, registry);
		ConfigFile global = readConfigFile("config", globalConfigDir);
		if (global == null) {
			System.err.println("Unable to read global configuration file");
		} else {
			activateDefaultPlugins(tool, global);
		}
		return tool;
	}

	private static ConfigFile readConfigFile(String name, Path.Root root) throws IOException {
		Path.Entry<ConfigFile> global = root.get(Trie.fromString(name), ConfigFile.ContentType);
		if (global != null) {
			try {
				return global.read();
			} catch (SyntaxError e) {
				e.outputSourceError(System.err, false);
				System.exit(-1);
			}
		}
		//
		return null;
	}

	private static boolean applyOption(Pair<String,Object> option, WyTool tool, Command command) {
		try {
			if(command == null) {
				// Configuration option for the tool
				tool.set(option.first(), option.second());
			} else {
				// Configuration option for the command
				command.set(option.first(), option.second());
			}
			return true;
		} catch (ConfigurationError e) {
			System.out.print("ERROR: ");
			System.out.println(e.getMessage());
			return false;
		}
	}

	/**
	 * Register the set of default commands that are included automatically
	 *
	 * @param tool
	 */
	private static void registerDefaultCommands(WyTool tool) {
		// The list of default commands available in the tool
		Command[] defaultCommands = {
				new Help(System.out,tool.getCommands())
				//new Build()
		};
		// Register the default commands available in the tool
		Module.Context context = tool.getContext();
		for(Command c : defaultCommands) {
			context.register(wycc.lang.Command.class,c);
		}
	}

	/**
	 * Activate the default set of plugins which the tool uses. Currently this
	 * list is statically determined, but eventually it will be possible to
	 * dynamically add plugins to the system.
	 *
	 * @param verbose
	 * @param locations
	 * @return
	 */
	private static void activateDefaultPlugins(WyTool tool, ConfigFile global) {
		Map<String,Object> plugins = (Map<String,Object>) global.toMap().get("plugins");
		if(plugins != null) {
			Module.Context context = tool.getContext();
			// create the context and manager

			// start modules
			for(String name : plugins.keySet()) {
				String activator = (String) plugins.get(name);
				try {
					Class<?> c = Class.forName(activator);
					Module.Activator instance = (Module.Activator) c.newInstance();
					instance.start(context);
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				} catch (InstantiationException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		} else {
			System.out.println("No plugin configuration found");
			System.exit(-1);
		}
	}

	/**
	 * Parse an option which is either a string of the form "--name" or
	 * "--name=data". Here, name is an arbitrary string and data is a string
	 * representing a data value.
	 *
	 * @param arg
	 *            The option argument to be parsed.
	 * @return
	 */
	private static Pair<String,Object> parseOption(String arg) {
		arg = arg.substring(2);
		String[] split = arg.split("=");
		Object data = null;
		if(split.length > 1) {
			data = parseData(split[1]);
		}
		return new Pair<>(split[0],data);
	}

	/**
	 * Parse a given string representing a data value into an instance of Data.
	 *
	 * @param str
	 *            The string to be parsed.
	 * @return
	 */
	private static Object parseData(String str) {
		if (str.equals("true")) {
			return true;
		} else if (str.equals("false")) {
			return false;
		} else if (Character.isDigit(str.charAt(0))) {
			// number
			return Integer.parseInt(str);
		} else {
			return str;
		}
	}

	/**
	 * Print a complete stack trace. This differs from
	 * Throwable.printStackTrace() in that it always prints all of the trace.
	 *
	 * @param out
	 * @param err
	 */
	private static void printStackTrace(PrintStream out, Throwable err) {
		out.println(err.getClass().getName() + ": " + err.getMessage());
		for(StackTraceElement ste : err.getStackTrace()) {
			out.println("\tat " + ste.toString());
		}
		if(err.getCause() != null) {
			out.print("Caused by: ");
			printStackTrace(out,err.getCause());
		}
	}
}
