package ch.cyberduck.ui.cocoa;

/*
 *  Copyright (c) 2003 David Kocher. All rights reserved.
 *  http://cyberduck.ch/
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Bug fixes, suggestions and comments should be sent to:
 *  dkocher@cyberduck.ch
 */

import ch.cyberduck.core.*;

import com.apple.cocoa.application.*;
import com.apple.cocoa.foundation.*;

import java.util.Observable;
import java.util.Observer;

import org.apache.log4j.Logger;

public class CDQueueController implements Observer, Validator {
	private static Logger log = Logger.getLogger(CDQueueController.class);

	private static CDQueueController instance;

	/**
	 * The observer to notify when a upload is complete
	 */
	private Observer callback;

	private NSToolbar toolbar;

	private static NSMutableArray instances = new NSMutableArray();

	public static CDQueueController instance() {
		if (null == instance) {
			instance = new CDQueueController();
			if (false == NSApplication.loadNibNamed("Queue", instance)) {
				log.fatal("Couldn't load Queue.nib");
			}
		}
		return instance;
	}

	private CDQueueController() {
		instances.addObject(this);
	}

	public void windowWillClose(NSNotification notification) {
		instances.removeObject(this);
	}

	private NSWindow window; // IBOutlet

	public void setWindow(NSWindow window) {
		this.window = window;
	}

	public NSWindow window() {
		return this.window;
	}

	private CDQueueTableDataSource queueModel;
	private NSTableView queueTable; // IBOutlet

	public void setQueueTable(NSTableView queueTable) {
		this.queueTable = queueTable;
		this.queueTable.setTarget(this);
		this.queueTable.setDoubleAction(new NSSelector("queueTableRowDoubleClicked", new Class[]{Object.class}));
		this.queueTable.setDataSource(this.queueModel = new CDQueueTableDataSource());
		this.queueTable.setDelegate(this.queueModel);
		// receive drag events from types
		// in fact we are not interested in file promises, but because the browser model can only initiate
		// a drag with tableView.dragPromisedFilesOfTypes(), we listens for those events
		// and then use the private pasteboard instead.
		this.queueTable.registerForDraggedTypes(new NSArray(
		    new Object[]{"QueuePBoardType",
		                 NSPasteboard.StringPboardType,
		                 NSPasteboard.FilesPromisePboardType}
		)
		);

		this.queueTable.setRowHeight(50f);

		NSTableColumn dataColumn = new NSTableColumn();
		dataColumn.setIdentifier("DATA");
		dataColumn.setMinWidth(200f);
		dataColumn.setWidth(350f);
		dataColumn.setMaxWidth(1000f);
		dataColumn.setEditable(false);
		dataColumn.setResizable(true);
		dataColumn.setDataCell(new CDQueueCell());
		this.queueTable.addTableColumn(dataColumn);

		NSTableColumn progressColumn = new NSTableColumn();
		progressColumn.setIdentifier("PROGRESS");
		progressColumn.setMinWidth(50f);
		progressColumn.setWidth(300f);
		progressColumn.setMaxWidth(1000f);
		progressColumn.setEditable(false);
		progressColumn.setResizable(true);
		progressColumn.setDataCell(new CDProgressCell());
		this.queueTable.addTableColumn(progressColumn);

		NSSelector setUsesAlternatingRowBackgroundColorsSelector =
		    new NSSelector("setUsesAlternatingRowBackgroundColors", new Class[]{boolean.class});
		if (setUsesAlternatingRowBackgroundColorsSelector.implementedByClass(NSTableView.class)) {
			this.queueTable.setUsesAlternatingRowBackgroundColors(true);
		}
		NSSelector setGridStyleMaskSelector =
		    new NSSelector("setGridStyleMask", new Class[]{int.class});
		if (setGridStyleMaskSelector.implementedByClass(NSTableView.class)) {
			this.queueTable.setGridStyleMask(NSTableView.SolidHorizontalGridLineMask);
		}

		this.queueTable.sizeToFit();

		// selection properties
		this.queueTable.setAllowsMultipleSelection(true);
		this.queueTable.setAllowsEmptySelection(true);
		this.queueTable.setAllowsColumnReordering(false);
	}

	public void addItem(Queue queue, boolean start, Observer callback) {
		this.addItem(queue, start);
		this.callback = callback;
	}

	public void addItem(Queue queue, boolean start) {
		this.window().makeKeyAndOrderFront(null);
		CDQueuesImpl.instance().addItem(queue);
		this.queueTable.reloadData();
		this.queueTable.selectRow(this.queueTable.numberOfRows() - 1, false);
		if (start)
			this.startItem(queue);
	}

	public void startItem(Queue queue) {
		queue.addObserver(this);
		queue.getRoot().getHost().getLogin().setController(new CDLoginController(this.window()));
		if (queue.getRoot().getHost().getProtocol().equals(Session.SFTP)) {
			try {
				queue.getRoot().getHost().setHostKeyVerificationController(new CDHostKeyController(this.window()));
			}
			catch (com.sshtools.j2ssh.transport.InvalidHostFileException e) {
				//This exception is thrown whenever an exception occurs open or reading from the host file.
				NSAlertPanel.beginCriticalAlertSheet(
				    NSBundle.localizedString("Error", ""), //title
				    NSBundle.localizedString("OK", ""), // defaultbutton
				    null, //alternative button
				    null, //other button
				    this.window(), //docWindow
				    null, //modalDelegate
				    null, //didEndSelector
				    null, // dismiss selector
				    null, // context
				    NSBundle.localizedString("Could not open or read the host file", "") + ": " + e.getMessage() // message
				);
			}
		}
		queue.start(this);
	}

	public void update(Observable observable, Object arg) {
//		log.debug("update:"+observable+","+arg);
		if (arg instanceof Message) {
			Message msg = (Message) arg;

			if (this.window().isVisible()) {
				if (this.queueTable.visibleRect() != NSRect.ZeroRect) {
//					log.debug("Queue table visible, redrawing cells");
					int row = CDQueuesImpl.instance().indexOf((Queue) observable);
					
//					NSCell queueCell = this.queueTable.tableColumnWithIdentifier("DATA").dataCell();
					NSRect queueRect = this.queueTable.frameOfCellAtLocation(0, row);
//					queueCell.drawInteriorWithFrameInView(queueRect, this.queueTable);

//					NSCell progressCell = this.queueTable.tableColumnWithIdentifier("PROGRESS").dataCell();
					NSRect progressRect = this.queueTable.frameOfCellAtLocation(1, row);
//					progressCell.drawInteriorWithFrameInView(progressRect, this.queueTable);

					this.queueTable.setNeedsDisplay(queueRect.rectByUnioningRect(progressRect));
				}
			}

			if (msg.getTitle().equals(Message.QUEUE_START) || msg.getTitle().equals(Message.QUEUE_STOP)) {
				this.toolbar.validateVisibleItems();
				Queue queue = (Queue) observable;
				if (Queue.KIND_UPLOAD == queue.kind()) {
					if (callback != null)
						callback.update(observable, queue.getRoot());
				}
			}
			else if (msg.getTitle().equals(Message.START)) {
				log.debug("************START***********");
				this.toolbar.validateVisibleItems();
			}
			else if (msg.getTitle().equals(Message.STOP)) {
				log.debug("************STOP***********");
				this.toolbar.validateVisibleItems();
				if (observable instanceof Queue) {
					Queue queue = (Queue) observable;
					if (queue.numberOfJobs() == queue.processedJobs()) {
						if (Preferences.instance().getProperty("queue.removeItemWhenComplete").equals("true")) {
							this.queueTable.deselectAll(null);
							CDQueuesImpl.instance().removeItem(queue);
							this.queueTable.reloadData();
						}
						if (Queue.KIND_DOWNLOAD == queue.kind()) {
							if (Preferences.instance().getProperty("queue.postProcessItemWhenComplete").equals("true")) {
								boolean success = NSWorkspace.sharedWorkspace().openFile(queue.getRoot().getLocal().toString());
								log.debug("Success opening file:" + success);
							}
						}
					}
				}
			}
			else if (msg.getTitle().equals(Message.ERROR)) {
				this.toolbar.validateVisibleItems();
				NSAlertPanel.beginCriticalAlertSheet(
				    NSBundle.localizedString("Error", ""), //title
				    NSBundle.localizedString("OK", ""), // defaultbutton
				    null, //alternative button
				    null, //other button
				    this.window(), //docWindow
				    null, //modalDelegate
				    null, //didEndSelector
				    null, // dismiss selector
				    null, // context
				    (String) msg.getContent() // message
				);
			}
		}
	}

	public void awakeFromNib() {
		this.window().setTitle("Transfer Queue");
		this.toolbar = new NSToolbar("Queue Toolbar");
		this.toolbar.setDelegate(this);
		this.toolbar.setAllowsUserCustomization(true);
		this.toolbar.setAutosavesConfiguration(true);
		this.window().setToolbar(toolbar);
	}

	// ----------------------------------------------------------
	// Toolbar Delegate
	// ----------------------------------------------------------

	public NSToolbarItem toolbarItemForItemIdentifier(NSToolbar toolbar, String itemIdentifier, boolean flag) {
		NSToolbarItem item = new NSToolbarItem(itemIdentifier);
		if (itemIdentifier.equals("Stop")) {
			item.setLabel(NSBundle.localizedString("Stop", ""));
			item.setPaletteLabel(NSBundle.localizedString("Stop", ""));
			item.setImage(NSImage.imageNamed("stop.tiff"));
			item.setTarget(this);
			item.setAction(new NSSelector("stopButtonClicked", new Class[]{Object.class}));
		}
		if (itemIdentifier.equals("Resume")) {
			item.setLabel(NSBundle.localizedString("Resume", ""));
			item.setPaletteLabel(NSBundle.localizedString("Resume", ""));
			item.setImage(NSImage.imageNamed("resume.tiff"));
			item.setTarget(this);
			item.setAction(new NSSelector("resumeButtonClicked", new Class[]{Object.class}));
		}
		if (itemIdentifier.equals("Reload")) {
			item.setLabel(NSBundle.localizedString("Reload", ""));
			item.setPaletteLabel(NSBundle.localizedString("Reload", ""));
			item.setImage(NSImage.imageNamed("reload.tiff"));
			item.setTarget(this);
			item.setAction(new NSSelector("reloadButtonClicked", new Class[]{Object.class}));
		}
		if (itemIdentifier.equals("Show")) {
			item.setLabel(NSBundle.localizedString("Show", ""));
			item.setPaletteLabel(NSBundle.localizedString("Show in Finder", ""));
			item.setImage(NSImage.imageNamed("reveal.tiff"));
			item.setTarget(this);
			item.setAction(new NSSelector("revealButtonClicked", new Class[]{Object.class}));
		}
		if (itemIdentifier.equals("Remove")) {
			item.setLabel(NSBundle.localizedString("Remove", ""));
			item.setPaletteLabel(NSBundle.localizedString("Remove", ""));
			item.setImage(NSImage.imageNamed("clean.tiff"));
			item.setTarget(this);
			item.setAction(new NSSelector("removeButtonClicked", new Class[]{Object.class}));
		}
		return item;
	}

	public void queueTableRowDoubleClicked(Object sender) {
		if (this.queueTable.selectedRow() != -1) {
			Queue item = CDQueuesImpl.instance().getItem(this.queueTable.selectedRow());
			if (item.isEmpty())
				this.revealButtonClicked(sender);
			else
				this.resumeButtonClicked(sender);
		}
	}

	public void stopButtonClicked(Object sender) {
		NSEnumerator enum = queueTable.selectedRowEnumerator();
		while (enum.hasMoreElements()) {
			Queue item = CDQueuesImpl.instance().getItem(((Integer) enum.nextElement()).intValue());
			if (item.isRunning())
				item.cancel();
		}
	}

	public void resumeButtonClicked(Object sender) {
		if (this.queueTable.selectedRow() != -1) {
			Queue item = CDQueuesImpl.instance().getItem(this.queueTable.selectedRow());
			if (!item.isRunning()) {
				item.getRoot().status.setResume(true);
				this.startItem(item);
			}
		}
	}

	public void reloadButtonClicked(Object sender) {
		if (this.queueTable.selectedRow() != -1) {
			Queue item = CDQueuesImpl.instance().getItem(this.queueTable.selectedRow());
			if (!item.isRunning()) {
				item.getRoot().status.setResume(false);
				this.startItem(item);
			}
		}
	}

	public void revealButtonClicked(Object sender) {
		if (this.queueTable.selectedRow() != -1) {
			Queue item = CDQueuesImpl.instance().getItem(this.queueTable.selectedRow());
			if (!NSWorkspace.sharedWorkspace().selectFile(item.getRoot().getLocal().toString(), "")) {
				if (item.isEmpty()) {
					NSAlertPanel.beginCriticalAlertSheet(
					    NSBundle.localizedString("Could not show the file in the Finder", ""), //title
					    NSBundle.localizedString("OK", ""), // defaultbutton
					    null, //alternative button
					    null, //other button
					    this.window(), //docWindow
					    null, //modalDelegate
					    null, //didEndSelector
					    null, // dismiss selector
					    null, // context
					    NSBundle.localizedString("Could not show the file", "") + " \""
					    + item.getRoot().getLocal().toString()
					    + "\". " + NSBundle.localizedString("It moved since you downloaded it.", "") // message
					);
				}
				else {
					NSAlertPanel.beginCriticalAlertSheet(
					    NSBundle.localizedString("Could not show the file in the Finder", ""), //title
					    NSBundle.localizedString("OK", ""), // defaultbutton
					    null, //alternative button
					    null, //other button
					    this.window(), //docWindow
					    null, //modalDelegate
					    null, //didEndSelector
					    null, // dismiss selector
					    null, // context
					    NSBundle.localizedString("Could not show the file", "") + " \""
					    + item.getRoot().getLocal().toString()
					    + "\". " + NSBundle.localizedString("The file has not yet been downloaded.", "") // message
					);
				}
			}
		}
	}

	public void removeButtonClicked(Object sender) {
		NSEnumerator enum = queueTable.selectedRowEnumerator();
		int i = 0;
		while (enum.hasMoreElements()) {
			CDQueuesImpl.instance().removeItem(((Integer) enum.nextElement()).intValue() - i);
			i++;
		}
		this.queueTable.reloadData();
	}

	public NSArray toolbarDefaultItemIdentifiers(NSToolbar toolbar) {
		return new NSArray(new Object[]{
			"Resume",
			"Reload",
			"Stop",
			"Remove",
			NSToolbarItem.FlexibleSpaceItemIdentifier,
			"Show"
		});
	}

	public NSArray toolbarAllowedItemIdentifiers(NSToolbar toolbar) {
		return new NSArray(new Object[]{
			"Resume",
			"Reload",
			"Stop",
			"Remove",
			"Show",
			NSToolbarItem.CustomizeToolbarItemIdentifier,
			NSToolbarItem.SpaceItemIdentifier,
			NSToolbarItem.SeparatorItemIdentifier,
			NSToolbarItem.FlexibleSpaceItemIdentifier
		});
	}

	public boolean validateToolbarItem(NSToolbarItem item) {
//		log.debug("validateToolbarItem:"+item.label());
		String identifier = item.itemIdentifier();
		if (identifier.equals("Stop")) {
			if (this.queueTable.numberOfSelectedRows() == 1) {
				Queue queue = CDQueuesImpl.instance().getItem(this.queueTable.selectedRow());
				return queue.isRunning();
			}
			return false;
		}
		if (identifier.equals("Resume")) {
			if (this.queueTable.numberOfSelectedRows() == 1) {
				Queue queue = CDQueuesImpl.instance().getItem(this.queueTable.selectedRow());
				return queue.isCanceled() && !(queue.remainingJobs() == 0);
			}
			return false;
		}
		if (identifier.equals("Reload")) {
			if (this.queueTable.numberOfSelectedRows() == 1) {
				Queue queue = CDQueuesImpl.instance().getItem(this.queueTable.selectedRow());
				return !queue.isRunning();
			}
			return false;
		}
		if (identifier.equals("Show")) {
			return this.queueTable.numberOfSelectedRows() == 1;
		}
		if (identifier.equals("Remove")) {
			if (this.queueTable.selectedRow() != -1) {
				Queue queue = CDQueuesImpl.instance().getItem(this.queueTable.selectedRow());
				return queue.isCanceled();
			}
			return false;
		}
		return true;
	}

	private boolean proceed;
	private boolean done;

	/**
	 * @return true if validation suceeded, false if !proceed
	 */
	public boolean validate(Path path, int kind) {
		boolean resume = path.status.isResume();
		this.done = false;
		this.proceed = false;
		log.debug("validate:" + path + "," + resume);
		if (Queue.KIND_DOWNLOAD == kind) {
			log.debug("validating download");
			if (resume) {
				log.debug("resume:true");
				if (path.status.isComplete()) {
					log.debug("complete:true");
					log.debug("return:true");
					return true;
				}
				else if (!path.status.isComplete()) {
					log.debug("complete:false");
					path.status.setResume(path.getLocal().exists());
					log.debug("return:true");
					return true;
				}
			}
			if (!resume) {
				log.debug("resume:false");
				if (path.getLocal().exists()) {
					log.debug("local path exists:true");
					if (Preferences.instance().getProperty("queue.download.duplicate").equals("ask")) {
						log.debug("queue.download.duplicate:ask");
						NSAlertPanel.beginCriticalAlertSheet(
						    NSBundle.localizedString("File exists", ""), //title
						    NSBundle.localizedString("Resume", ""), // defaultbutton
						    NSBundle.localizedString("Cancel", ""), //alternative button
						    NSBundle.localizedString("Overwrite", ""), //other button
						    this.window(),
						    this, //delegate
						    new NSSelector
						        (
						            "validateSheetDidEnd",
						            new Class[]
						            {
							            NSWindow.class, int.class, Object.class
						            }
						        ), // end selector
						    null, // dismiss selector
						    path, // context
															 NSBundle.localizedString("The file", "") + " " + path.getName() + " " + NSBundle.localizedString("alredy exists in", "") + " " + path.getLocal().getParent() // message
//						    NSBundle.localizedString("The file", "") + " " + Codec.decode(path.getName()) + " " + NSBundle.localizedString("alredy exists in", "") + " " + path.getLocal().getParent() // message
						);
						while (!done) {
							try {
								log.debug("Sleeping...");
								Thread.sleep(1000); //milliseconds
							}
							catch (InterruptedException e) {
								log.error(e.getMessage());
							}
						}
						log.debug("return:" + proceed);
						return proceed;
					}
					else if (Preferences.instance().getProperty("queue.download.duplicate").equals("similar")) {
						log.debug("queue.download.duplicate:similar");
						path.status.setResume(false);
						String proposal = null;
						String parent = path.getLocal().getParent();
						String filename = path.getLocal().getName();
						int no = 1;
						int index = filename.lastIndexOf(".");
						while (path.getLocal().exists()) {
							if (index != -1)
								proposal = filename.substring(0, index) + "-" + no + filename.substring(index);
							else
								proposal = filename + "-" + no;
							path.setLocal(new Local(parent, proposal));
							no++;
						}
						log.debug("return:true");
						return true;
					}
					else if (Preferences.instance().getProperty("queue.download.duplicate").equals("resume")) {
						log.debug("queue.download.duplicate:resume");
						path.status.setResume(true);
						log.debug("return:true");
						return true;
					}
					else if (Preferences.instance().getProperty("queue.download.duplicate").equals("overwrite")) {
						log.debug("queue.download.duplicate:overwrite");
						path.status.setResume(false);
						log.debug("return:true");
						return true;
					}
				}
				log.debug("local path exists:false");
				log.debug("return:true");
				return true;
			}
		}
		else if (Queue.KIND_UPLOAD == kind) {
			log.debug("Validating upload");
			path.status.setResume(false);
			log.debug("return:true");
			return true;
		}
		return false;
	}

	public void validateSheetDidEnd(NSWindow sheet, int returncode, Object contextInfo) {
		log.debug("validateSheetDidEnd:" + returncode + "," + contextInfo);
		sheet.close();
		Path item = (Path) contextInfo;
		switch (returncode) {
			case NSAlertPanel.DefaultReturn: //Resume
				item.status.setResume(true);
				proceed = true;
				break;
			case NSAlertPanel.AlternateReturn: //Cancel
				proceed = false;
				break;
			case NSAlertPanel.OtherReturn: //Overwrite
				item.status.setResume(false);
				proceed = true;
				break;
		}
		this.done = true;
	}

//	public void closeSheetDidEnd(NSWindow sheet, int returncode, Object contextInfo) {
//		log.debug("closeSheetDidEnd");
//		sheet.orderOut(null);
//		switch (returncode) {
//			case NSAlertPanel.DefaultReturn:
//				this.stopButtonClicked(null);
//				this.window().close();
//				break;
//			case NSAlertPanel.AlternateReturn:
//				break;
//		}
//	}
}