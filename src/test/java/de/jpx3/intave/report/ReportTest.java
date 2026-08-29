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
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class ReportTest {
  @Test
  void serializesReportAsNestedJson() throws IOException {
    UUID suspectId = UUID.fromString("fa5c659a-c937-4f51-b25b-1533b4e4d996");
    User suspect = userWithId(suspectId);
    Report report = new Report() {
      @Override
      public User suspect() {
        return suspect;
      }

      @Override
      public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "physics");
        return json;
      }
    };

    StringWriter output = new StringWriter();
    try (JsonWriter writer = new JsonWriter(output)) {
      report.serialize(writer);
    }

    JsonObject serialized = new JsonParser().parse(output.toString()).getAsJsonObject();
    assertEquals(suspectId.toString(), serialized.get("suspect").getAsString());
    assertTrue(serialized.get("report").isJsonObject());
    assertEquals("physics", serialized.getAsJsonObject("report").get("type").getAsString());
  }

  @Test
  void deserializesSuspectAndNestedReport() throws IOException {
    UUID suspectId = UUID.fromString("c297d21c-9f17-42fd-bcad-b6a5e41f354f");
    User suspect = userWithId(suspectId);
    Player player = playerWithId(suspectId);
    MinecraftVersion.setCurrent(new MinecraftVersion("1.21.4"));
    UserRepository.manuallyRegisterUser(player, suspect);
    try {
      String json = "{"
        + "\"ignored\":{\"value\":true},"
        + "\"report\":{\"type\":\"physics\",\"violations\":3},"
        + "\"suspect\":\"" + suspectId + "\""
        + "}";

      Report deserialized;
      try (JsonReader reader = new JsonReader(new StringReader(json))) {
        deserialized = Report.deserialize(reader);
      }

      assertSame(suspect, deserialized.suspect());
      assertEquals("physics", deserialized.toJson().get("type").getAsString());
      assertEquals(3, deserialized.toJson().get("violations").getAsInt());
    } finally {
      UserRepository.unregisterUser(player);
    }
  }

  private static User userWithId(UUID id) {
    return (User) Proxy.newProxyInstance(
      User.class.getClassLoader(),
      new Class<?>[]{User.class},
      (proxy, method, arguments) -> method.getName().equals("id") ? id : null
    );
  }

  private static Player playerWithId(UUID id) {
    return (Player) Proxy.newProxyInstance(
      Player.class.getClassLoader(),
      new Class<?>[]{Player.class},
      (proxy, method, arguments) -> method.getName().equals("getUniqueId") ? id : null
    );
  }
}
