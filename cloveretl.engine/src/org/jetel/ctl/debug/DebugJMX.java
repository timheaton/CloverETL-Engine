/*
 * jETeL/CloverETL - Java based ETL application framework.
 * Copyright (c) Javlin, a.s. (info@cloveretl.com)
 *  
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jetel.ctl.debug;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;

import org.jetel.ctl.ASTnode.CLVFFunctionCall;
import org.jetel.ctl.debug.DebugCommand.CommandType;

/**
 * @author Magdalena Malysz (info@cloveretl.com)
 *         (c) Javlin, a.s. (www.cloveretl.com)
 *
 * @created 11. 3. 2016
 */
public class DebugJMX extends NotificationBroadcasterSupport implements DebugJMXMBean {
	
	private int notificationSequence;
	
	private Map<Long, CTLDebugThread> activeThreads;
	
	public DebugJMX() {
		activeThreads = new HashMap<>();
	}
	
	public synchronized void registerCTLThread(Thread thread, ArrayBlockingQueue<DebugCommand> commandQueue, ArrayBlockingQueue<DebugStatus> statusQueue) {
		activeThreads.put(thread.getId(), new CTLDebugThread(thread, commandQueue, statusQueue));
	}
	
	public synchronized void unregisterCTLDebugThread(Thread thread) {
		activeThreads.remove(thread.getId());
	}
	
	public void notifySuspend(DebugStatus status) {
		Notification suspendNotification = new Notification(DebugJMX.THREAD_SUSPENDED, this, notificationSequence++);
		suspendNotification.setUserData(status);
		sendNotification(suspendNotification);
	}
	
	private DebugStatus processCommand(long threadId, DebugCommand command) {
		CTLDebugThread thread = activeThreads.get(threadId);
		if (thread != null) { 
			thread.putCommand(command);
			DebugStatus status = thread.takeCommand();
			return status;

		}
		// TODO error
		return null;
	}
	
	@Override
	public synchronized void resume(long threadId) {
		DebugCommand dcommand = new DebugCommand(CommandType.RESUME);
		processCommand(threadId, dcommand);
	}
	
	@Override
	public synchronized void info(long threadId) {
		processCommand(threadId, new DebugCommand(CommandType.INFO));
	}
	
	@Override
	public synchronized Thread[] listCtlThreads() {
		Thread[] threads = new Thread[activeThreads.size()];
		int i = 0;
		for (CTLDebugThread threadInfo : activeThreads.values()) {
			threads[i++] = threadInfo.getThread();
		}
		return threads;
	}

	@Override
	public synchronized StackFrame[] getStackFrames(long threadId) {
		DebugCommand dcommand = new DebugCommand(CommandType.GET_CALLSTACK);
		DebugStatus status = processCommand(threadId, dcommand);
		CLVFFunctionCall calls[] = (CLVFFunctionCall[])status.getValue();
		Arrays.sort(calls);
		StackFrame[] stackFrames = new StackFrame[calls.length];
		int i = 0;
		for (CLVFFunctionCall point:calls){
			StackFrame stackFrame = new StackFrame();
			stackFrame.setLineNumber(point.getLine());
			stackFrame.setName(point.getName());
			stackFrames[i++] = stackFrame;
		}
		return stackFrames;
	}


	@Override
	public synchronized void resumeAll() {
		for (Long threadId : activeThreads.keySet()) {
			DebugCommand dcommand = new DebugCommand(CommandType.RESUME);
			processCommand(threadId, dcommand);
		}
	}
	
	public static String createMBeanName(String graphId, long runId) {
        return "org.jetel.ctl:type=DebugJMX_" + graphId + "_" + runId;
	}

}
