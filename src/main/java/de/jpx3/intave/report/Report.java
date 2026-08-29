/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.report;

import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;

import java.io.IOException;
import java.util.UUID;

public interface Report {
	User suspect();

	JsonObject toJson();

	default void serialize(JsonWriter writer) {
		try {
			writer.beginObject();
			writer.name("suspect").value(suspect().id().toString());
			writer.name("report");
			Streams.write(toJson(), writer);
			writer.endObject();
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}

	static Report deserialize(JsonReader reader) {
		try {
			UUID suspectId = null;
			JsonObject report = null;
			reader.beginObject();
			while (reader.hasNext()) {
				switch (reader.nextName()) {
					case "suspect":
						suspectId = UUID.fromString(reader.nextString());
						break;
					case "report":
						report = Streams.parse(reader).getAsJsonObject();
						break;
					default:
						reader.skipValue();
						break;
				}
			}
			reader.endObject();

			if (suspectId == null) {
				throw new IllegalStateException("Missing report suspect");
			}
			if (report == null) {
				throw new IllegalStateException("Missing report payload");
			}

			User suspect = UserRepository.userOf(suspectId);
			JsonObject payload = report;
			return new Report() {
				@Override
				public User suspect() {
					return suspect;
				}

				@Override
				public JsonObject toJson() {
					return payload;
				}
			};
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}
}
