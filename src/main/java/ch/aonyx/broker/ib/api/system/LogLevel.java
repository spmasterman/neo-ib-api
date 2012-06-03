/*
 * Copyright (C) 2012 Aonyx
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.aonyx.broker.ib.api.system;

/**
 * @author Christophe Marcourt
 * @version 1.0.0
 */
public enum LogLevel {

	SYSTEM(1), ERROR(2), WARNING(3), INFO(4), DETAIL(5);

	private final int value;

	private LogLevel(final int value) {
		this.value = value;
	}

	public final int getValue() {
		return value;
	};
}
