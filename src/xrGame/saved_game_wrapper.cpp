////////////////////////////////////////////////////////////////////////////
//	Module 		: saved_game_wrapper.cpp
//	Created 	: 21.02.2006
//  Modified 	: 21.02.2006
//	Author		: Dmitriy Iassenev
//	Description : saved game wrapper class
////////////////////////////////////////////////////////////////////////////

#include "StdAfx.h"
#include "saved_game_wrapper.h"
#include "alife_time_manager.h"
#include "alife_object_registry.h"
#include "xrServer_Objects_ALife_Monsters.h"
#include "ai_space.h"
#include "xrAICore/Navigation/game_graph.h"
#include "alife_simulator_header.h"
#include "alife_simulator.h"
#include "alife_spawn_registry.h"

extern LPCSTR alife_section;

pcstr CSavedGameWrapper::saved_game_full_name(pcstr saved_game_name, string_path& result, pcstr extension)
{
    string_path temp;
    strconcat(sizeof(temp), temp, saved_game_name, extension);
    FS.update_path(result, "$game_saves$", temp);
    return (result);
}

bool CSavedGameWrapper::saved_game_exist(LPCSTR saved_game_name)
{
    string_path file_name;
    if (FS.exist(saved_game_full_name(saved_game_name, file_name, SAVE_EXTENSION)))
        return true;
    return FS.exist(saved_game_full_name(saved_game_name, file_name, SAVE_EXTENSION_LEGACY));
}

bool CSavedGameWrapper::valid_saved_game(IReader& stream)
{
    if (stream.length() < 8)
        return (false);

    if (stream.r_u32() != u32(-1))
        return (false);

    if (stream.r_u32() < ALIFE_VERSION)
        return (false);

    return (true);
}

bool CSavedGameWrapper::valid_saved_game(LPCSTR saved_game_name)
{
    string_path file_name;
    if (!FS.exist(saved_game_full_name(saved_game_name, file_name, SAVE_EXTENSION)))
        if (!FS.exist(saved_game_full_name(saved_game_name, file_name, SAVE_EXTENSION_LEGACY)))
        {
            Msg(">>> [CSavedGameWrapper] valid_saved_game('%s'): file not found!", saved_game_name ? saved_game_name : "<null>");
            return false;
        }

    IReader* stream = FS.r_open(file_name);
    bool result = valid_saved_game(*stream);
    FS.r_close(stream);
    Msg(">>> [CSavedGameWrapper] valid_saved_game('%s'): stream valid=%d (file: %s)", saved_game_name, (int)result, file_name);
    return (result);
}

CSavedGameWrapper::CSavedGameWrapper(LPCSTR saved_game_name)
{
    string_path file_name;
    saved_game_full_name(saved_game_name, file_name, SAVE_EXTENSION);
    if (!FS.exist(file_name))
        saved_game_full_name(saved_game_name, file_name, SAVE_EXTENSION_LEGACY);

    Msg(">>> [CSavedGameWrapper] Constructor for '%s' (file: '%s')", saved_game_name ? saved_game_name : "<null>", file_name);
    R_ASSERT3(FS.exist(file_name), "There is no saved game ", saved_game_name);

    IReader* stream = FS.r_open(file_name);
    if (!valid_saved_game(*stream))
    {
        Msg(">>> [CSavedGameWrapper] stream not valid for '%s'", file_name);
        FS.r_close(stream);
        CALifeTimeManager time_manager(alife_section);
        m_game_time = time_manager.game_time();
        m_actor_health = 1.f;
        m_level_id = _LEVEL_ID(-1);
        m_level_name = "";
        return;
    }

    u32 source_count = stream->r_u32();
    void* source_data = xr_malloc(source_count);
    const u32 compressed_size = stream->length() - 3 * sizeof(u32);
    Msg(">>> [CSavedGameWrapper] Calling rtc_decompress: source_count=%u, compressed_size=%u...", source_count, compressed_size);
    rtc_decompress(source_data, source_count, stream->pointer(), compressed_size);
    Msg(">>> [CSavedGameWrapper] rtc_decompress done! Closing stream and parsing reader...");
    FS.r_close(stream);

    IReader reader(source_data, source_count);

    {
        CALifeTimeManager time_manager(alife_section);
        time_manager.load(reader);
        m_game_time = time_manager.game_time();
    }

    {
        R_ASSERT2(reader.find_chunk(OBJECT_CHUNK_DATA), "Can't find chunk OBJECT_CHUNK_DATA!");
        [[maybe_unused]] u32 count = reader.r_u32();
        VERIFY(count > 0);
        CSE_ALifeDynamicObject* object = CALifeObjectRegistry::get_object(reader);
        VERIFY(object->ID == 0);
        CSE_ALifeCreatureActor* actor = smart_cast<CSE_ALifeCreatureActor*>(object);
        VERIFY(actor);

        m_actor_health = actor->get_health();

        IReader* chunk = reader.open_chunk(SPAWN_CHUNK_DATA);
        R_ASSERT2(chunk, "Spawn version mismatch - REBUILD SPAWN!");

        string_path spawn_file_name;
        {
            IReader* sub_chunk = chunk->open_chunk(0);
            if (!sub_chunk)
            {
                chunk->close();
                F_entity_Destroy(object);
                m_level_id = _LEVEL_ID(-1);
                m_level_name = "";
                return;
            }
            sub_chunk->r_stringZ(spawn_file_name, sizeof(spawn_file_name));
            sub_chunk->close();
        }

        chunk->close();

        if (!FS.exist(file_name, "$game_spawn$", spawn_file_name, ".spawn"))
        {
            F_entity_Destroy(object);
            m_level_id = _LEVEL_ID(-1);
            m_level_name = "";
            return;
        }

        IReader* spawn = NULL;
        bool b_destroy_spawn = true;
        if (ai().get_alife() && ai().alife().spawns().get_spawn_name() == spawn_file_name)
        {
            spawn = ai().alife().spawns().get_spawn_file();
            b_destroy_spawn = false;
        }
        else
            spawn = FS.r_open(file_name);

        if (!spawn)
        {
            F_entity_Destroy(object);
            m_level_id = _LEVEL_ID(-1);
            m_level_name = "";
            return;
        }

        chunk = spawn->open_chunk(4);
        if (!chunk) // Shadow of Chernobyl
        {
            string_path graph_path;
            FS.update_path(graph_path, "$game_data$", GRAPH_NAME);
            chunk = FS.r_open(graph_path);
        }

        if (!chunk)
        {
            F_entity_Destroy(object);
            if (b_destroy_spawn)
                FS.r_close(spawn);
            m_level_id = _LEVEL_ID(-1);
            m_level_name = "";
            return;
        }

        {
            CGameGraph graph(*chunk);
            m_level_id = graph.vertex(object->m_tGraphID)->level_id();
            if (graph.header().level_exist(m_level_id))
                m_level_name = graph.header().level(m_level_id).name();
            else
                m_level_name = StringTable().translate("ui_st_error");
            Msg(">>> [CSavedGameWrapper] Parsed successfully: m_level_id=%u, m_level_name='%s', actor_health=%.2f",
                (u32)m_level_id, m_level_name.c_str(), m_actor_health);
        }

        chunk->close();
        if (b_destroy_spawn)
            FS.r_close(spawn);
        F_entity_Destroy(object);
    }

    xr_free(source_data);
}
