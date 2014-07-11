/*
 * MibbleBrowser.java
 *
 * This work is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 2 of the License,
 * or (at your option) any later version.
 *
 * This work is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA
 *
 * Copyright (c) 2004-2013 Per Cederberg. All rights reserved.
 */

package net.percederberg.mibble;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.prefs.Preferences;

import javax.swing.UIManager;

import net.percederberg.mibble.browser.BrowserFrame;
import net.percederberg.mibble.browser.MibTreeBuilder;
import net.percederberg.mibble.value.ObjectIdentifierValue;

/**
 * A program for browsing MIB files in a GUI.
 *
 * @author   Per Cederberg, <per at percederberg dot net>
 * @version  2.10
 * @since    2.3
 */
public class MibbleBrowser {

    /**
     * The command-line help output.
     */
    private static final String COMMAND_HELP =
        "A graphical SNMP MIB file browser. This program comes with\n" +
        "ABSOLUTELY NO WARRANTY; for details see the LICENSE.txt file.\n" +
        "\n" +
        "Syntax: MibbleBrowser [<file(s) or URL(s)>]";

    /**
     * The internal error message.
     */
    private static final String INTERNAL_ERROR =
        "INTERNAL ERROR: An internal error has been found. Please report\n" +
        "    this error to the maintainers (see the web site for\n" +
        "    instructions). Be sure to include the version number, as\n" +
        "    well as the text below:\n";

    /**
     * The application build information properties.
     */
    private Properties buildInfo;

    /**
     * The preferences for this application.
     */
    private Preferences prefs;

    /**
     * The MIB loader to use.
     */
    public MibLoader loader = new MibLoader();

    /**
     * The application main entry point.
     *
     * @param args           the command-line parameters
     */
    public static void main(String args[]) {
        new MibbleBrowser().start(args);
    }

    /**
     * Creates a new browser application.
     */
    public MibbleBrowser() {
        prefs = Preferences.userNodeForPackage(getClass());
    }

    /**
     * Starts this application.
     *
     * @param args           the command-line arguments
     */
    public void start(String[] args) {

        // Check command-line arguments
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                printHelp("No option '" + args[i] + "' exist");
                System.exit(1);
            }
        }

        // Load application build information
        buildInfo = new Properties();
        try {
            buildInfo.load(getClass().getResourceAsStream("build.properties"));
        } catch (IOException ignore) {
            buildInfo.setProperty("build.title", "Mibble");
        }

        // Open browser frame
        try {
            String str = "com.apple.mrj.application.apple.menu.about.name";
            System.setProperty(str, buildInfo.getProperty("build.title"));
            str = UIManager.getSystemLookAndFeelClassName();
            UIManager.setLookAndFeel(str);
        } catch (Exception e) {
            printInternalError(e);
        }
        BrowserFrame frame = new BrowserFrame(this);
        frame.setVisible(true);

        // Load command-line & preference MIBs
        frame.setBlocked(true);
        ArrayList<String> list = getFilePrefs();
        for (int i = 0; i < args.length; i++) {
            list.add(args[i]);
        }
        for (int i = 0; i < list.size(); i++) {
            frame.loadMib(list.get(i).toString());
        }
        if (list.size() <= 0) {
            frame.loadMib("RFC1155-SMI");
            frame.loadMib("RFC1213-MIB");
            frame.loadMib("SNMPv2-SMI");
            frame.loadMib("SNMPv2-TC");
            frame.loadMib("HOST-RESOURCES-MIB");
        }
        frame.refreshTree();
        frame.setBlocked(false);
    }

    /**
     * Prints command-line help information.
     *
     * @param error          an optional error message, or null
     */
    private void printHelp(String error) {
        System.err.println(COMMAND_HELP);
        System.err.println();
        if (error != null) {
            printError(error);
        }
    }

    /**
     * Prints an internal error message. This type of error should
     * only be reported when run-time exceptions occur, such as null
     * pointer and the likes. All these error should be reported as
     * bugs to the program maintainers.
     *
     * @param e              the exception to be reported
     */
    private void printInternalError(Exception e) {
        System.err.println(INTERNAL_ERROR);
        e.printStackTrace();
    }

    /**
     * Prints an error message.
     *
     * @param message        the error message
     */
    private void printError(String message) {
        System.err.print("Error: ");
        System.err.println(message);
    }

    /**
     * Returns the application build information.
     *
     * @return the application build information
     */
    public Properties getBuildInfo() {
        return buildInfo;
    }

    public Mib getMib(String src) {
        MibTreeBuilder  mb = MibTreeBuilder.getInstance();
        File  file = new File(src);
        Mib   mib = null;
        if (file.exists()) {
                mib = loader.getMib(file);
        }
        if ( mib == null ) {
                mib = loader.getMib(src);
        }
        return mib;
    }

    public URL getURL( Mib m )
    {
         return loader.getURL( m );
    }

    /**
     * Loads MIB file or URL.
     *
     * @param src            the MIB file or URL
     *
     * @throws IOException if the MIB file couldn't be found in the
     *             MIB search path
     * @throws MibLoaderException if the MIB file couldn't be loaded
     *             correctly
     */
    public void loadMib(String src) throws IOException, MibLoaderException {
        Mib mib = null;
        File file = new File(src);
        if (file.exists()) {
            if (loader.getMib(file) != null) {
                return;
            }
            if (!loader.hasDir(file.getParentFile())) {
                loader.removeAllDirs();
                loader.addDir(file.getParentFile());
            }
            mib = loader.load(file);
            addFilePref(file.getAbsolutePath());
        } else {
            mib = loader.load(src);
            addFilePref(src);
        }
        MibTreeBuilder.getInstance().addMib(mib);
    }

    /**
     * Unloads a named MIB.
     *
     * @param name           the MIB name
     */
    public void unloadMib(String name) {
        Mib mib = loader.getMib(name);
        if (mib != null) {
            File file = mib.getFile();
            removeFilePref(file.getAbsolutePath());
            if (!file.exists()) {
                removeFilePref(mib.getName());
            }
            try {
                loader.unload(name);
            } catch (MibLoaderException ignore) {
                // MIB loader unloading is best-attempt only
            }
            MibTreeBuilder.getInstance().unloadMib(name);
        }
    }

    /**
     * Unloads all loaded MIB files.
     *
     * @since 2.9
     */
    public void unloadAllMibs() {
        removeFilePrefs();
        loader.unloadAll();
        MibTreeBuilder.getInstance().unloadAllMibs();
    }

    /**
    * Returns all MIBs found in the resources.
    *
    * @return a map containing the MIBs from the resources.  Each key of the Map is a 'resourceDir', and each value is a List of MIB files in that directory.
    */
    public Map getResourceMIBs()
    {
        ClassLoader classloader = getClass().getClassLoader();
        String[] dirs = loader.getResourceDirs();
        URL url;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile( ".*file:(.*\\.jar)!/?(.+).*" ).matcher("");
        Map mibPaths = new HashMap(4);
        String mibDir;
        List mibFiles;
        Enumeration mibEnum;
        JarFile jarFile;
        JarEntry jarEntry;

        for ( int i = 0; i < dirs.length; i++ ) {
                url = classloader.getResource( dirs[i] );
                if ( url != null ) {
                        m.reset(url.getFile());
                        if ( m.matches() && (m.groupCount() > 1) ) {
                                try {
                                        jarFile = new JarFile( m.group(1) );
                                        mibFiles = new LinkedList();
                                        mibDir = m.group(2);
                                        mibPaths.put( mibDir, mibFiles );
                                        mibEnum = jarFile.entries();
                                        while ( mibEnum.hasMoreElements() ) {
                                                jarEntry = (JarEntry) mibEnum.nextElement();
                                                if ( jarEntry.getName().startsWith( mibDir ) && !jarEntry.isDirectory() ) {
                                                        mibFiles.add( jarEntry.getName() );
                                                }
                                        }
                                } catch (IOException ioerr) {
                                        printInternalError(ioerr);
                                }
                        }
                }
        }

        //Each key in mibPaths is now a 'resourceDir' and its value is a list of MIBS the resourceDir contains.

        //sort the lists
        Iterator mibLists = mibPaths.values().iterator();
        while ( mibLists.hasNext() ) {
                Collections.sort( (List) mibLists.next() );
        }

        return mibPaths;
    }

    /**
     * Searches the OID tree from the loaded MIB files for the best
     * matching value. The returned OID symbol will have the longest
     * matching OID value, but doesn't have to be an exact match. The
     * search requires the full numeric OID value (from the root).
     *
     * @param oid            the numeric OID string to search for
     *
     * @return the best matching OID symbol, or
     *         null if no partial match was found
     *
     * @see MibLoader#getOid(String)
     * @since 2.10
     */
    public MibValueSymbol findMibSymbol(String oid) {
        ObjectIdentifierValue match = loader.getOid(oid);
        return (match == null) ? null : match.getSymbol();
    }

    /**
     * Adds a specified MIB file preference. The file may be either a built-in
     * MIB name or an absolute MIB file path.
     *
     * @param file           the MIB file or name to add
     */
    private void addFilePref(String file) {
        ArrayList<String> list = getFilePrefs();
        if (!list.contains(file)) {
            prefs.put("file" + list.size(), file);
        }
    }

    /**
     * Removes a specified MIB file preference. The file may be either a
     * built-in MIB name or an absolute MIB file path.
     *
     * @param file           the MIB file or name to remove
     */
    private void removeFilePref(String file) {
        ArrayList<String> list = getFilePrefs();
        removeFilePrefs();
        list.remove(file);
        for (int i = 0; i < list.size(); i++) {
            prefs.put("file" + i, list.get(i).toString());
        }
    }

    /**
     * Returns the application MIB file preferences.
     *
     * @return the list of MIB files to load
     */
    private ArrayList<String> getFilePrefs() {
        ArrayList<String> list = new ArrayList<String>();
        for (int i = 0; i < 1000; i++) {
            String str = prefs.get("file" + i, null);
            if (str != null) {
                list.add(str);
            }
        }
        return list;
    }

    /**
     * Removes all application MIB file preferences.
     */
    private void removeFilePrefs() {
        for (int i = 0; i < 1000; i++) {
            prefs.remove("file" + i);
        }
    }
}
