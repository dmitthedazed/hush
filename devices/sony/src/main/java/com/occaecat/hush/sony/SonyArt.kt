package com.occaecat.hush.sony

import android.content.Context
import android.graphics.Bitmap
import com.occaecat.hush.wired.RemoteRender

/**
 * Sony's product pictures for the MDR v1 and v2 models, from Sound Connect's cloud model list: GraphQL
 * `HPC { getAllCloudModelInfos { model_name model_color_id sca_image_image_url left_image_url right_image_url
 * cradle_image_url } }` on v1.api.data-gateway.seeds.services (docs/MDR_V1.md §5). The list is keyed like Sound
 * Connect's lookup, by model name and ModelColor; only the image ids are kept, the files are fetched once and cached.
 */
object SonyArt {
    /** Image ids on [BASE] (or a whole URL); [left]/[right]/[case] only for true wireless models. */
    data class Art(val render: String, val left: String? = null, val right: String? = null, val case: String? = null)

    enum class Part { RENDER, LEFT, RIGHT, CASE }

    private const val BASE = "https://hpc-image.data-gateway.seeds.services/"
    private const val SONY_SK = "https://www.sony.sk/image/"
    private const val SONY_SK_SIZE = "?fmt=png-alpha&wid=720&hei=720"

    private val table: Map<Pair<String, Int>, Art> = mapOf(
        "WH-1000XM3" to 0x00 to Art("dc832d01-d2a4-4c66-83c7-6708ef1e1749"),
        "WH-1000XM3" to 0x01 to Art("dc832d01-d2a4-4c66-83c7-6708ef1e1749"),
        "WH-1000XM3" to 0x03 to Art("0d84ce9f-cfea-4e35-9734-248942bcf80e"),
        "WH-1000XM2" to 0x00 to Art("23b6830a-cdaa-43f5-8ba4-d650b868f60a"),
        "WH-1000XM2" to 0x01 to Art("23b6830a-cdaa-43f5-8ba4-d650b868f60a"),
        "WH-1000XM2" to 0x0A to Art("415f42ad-c4e1-4bf1-8b6f-a3657148eb0c"),
        "WF-1000XM3" to 0x00 to Art("4357c544-a39a-4c45-8dd0-b75fa3db07d3", "a544841e-392c-4530-afe8-74dbe7854874", "aae4e3df-4809-4e39-80e9-4b6ffbeac2e3", "94b49b0d-449f-4217-b2ca-db7f17d98ab8"),
        "WF-1000XM3" to 0x01 to Art("4357c544-a39a-4c45-8dd0-b75fa3db07d3", "a544841e-392c-4530-afe8-74dbe7854874", "aae4e3df-4809-4e39-80e9-4b6ffbeac2e3", "94b49b0d-449f-4217-b2ca-db7f17d98ab8"),
        "WF-1000XM3" to 0x03 to Art("0fa64cf8-c69e-44da-8228-21d54897ed3e", "97698830-9d75-46d3-8bce-489704d12711", "6b8c8c66-8ef6-47f0-82c0-67df0f1a4fd5", "9a618488-98b0-4462-ba32-c523936c6e7d"),
        "WF-SP800N" to 0x00 to Art("0596b4ea-b189-4bd9-af19-5c4e97ed02fc", "0f476996-1e46-4562-9422-fda19ab54664", "6a937853-102e-4547-9bfe-f1f045e62c70", "818f57fd-8350-4582-909b-113da4215d61"),
        "WF-SP800N" to 0x01 to Art("0596b4ea-b189-4bd9-af19-5c4e97ed02fc", "0f476996-1e46-4562-9422-fda19ab54664", "6a937853-102e-4547-9bfe-f1f045e62c70", "818f57fd-8350-4582-909b-113da4215d61"),
        "WF-SP800N" to 0x02 to Art("180bc629-23ec-4250-bc08-bbbf09b87d3a", "251cc298-43bf-4375-82b4-b14dd8318471", "ccebfbc9-864c-416e-a801-54504746aed1", "66ec6267-3492-403d-89ae-66d19be48c96"),
        "WF-SP800N" to 0x05 to Art("ba722b95-8dc7-465c-bfc6-cd6e98cd039c", "f400306a-6ac9-4cd8-94c9-fac9943c86ce", "2254e60f-07fa-4ae6-ae08-3e80f1367782", "b95965a1-3b64-4a48-a346-7635a638c41b"),
        "WF-SP800N" to 0x0C to Art("0c6a064e-0bbc-421e-babd-0c7a53caea44", "e3f57b0c-3b62-498b-8463-2215f6d8d57b", "d2cdff25-3d37-49ad-a812-f8816bb50e83", "e0d529ab-3fc5-4ba3-9761-8769eb1c8bf0"),
        "WI-SP600N" to 0x00 to Art("cce4a9a0-7388-457b-9428-3c3ea23d65ac"),
        "WI-SP600N" to 0x01 to Art("cce4a9a0-7388-457b-9428-3c3ea23d65ac"),
        "WI-SP600N" to 0x02 to Art("eb1adb9c-b7b4-4a97-a31b-077c44a1f21f"),
        "WI-SP600N" to 0x05 to Art("6ae26ce5-51f9-4168-bdc6-0a18f4ef38d7"),
        "WI-SP600N" to 0x06 to Art("0fdb929b-22f7-480e-9d91-1331c5c581f3"),
        "WI-SP600N" to 0x07 to Art("81a4d5de-0f4e-40aa-84e4-85e60dc54995"),
        "WI-C100" to 0x00 to Art("06e0432c-d063-426a-9fd4-4fa8897e4b6a"),
        "WI-C100" to 0x01 to Art("06e0432c-d063-426a-9fd4-4fa8897e4b6a"),
        "WI-C100" to 0x02 to Art("6762de44-493b-4982-9d4a-1cc3964e4898"),
        "WI-C100" to 0x05 to Art("84f6c629-4fac-4bd0-a358-46ba4088518c"),
        "WI-C100" to 0x0B to Art("d33b1ffb-f456-4772-9766-8cafcaca1e65"),
        "WI-1000XM2" to 0x00 to Art("6486bef9-9ebb-4223-97f9-bea90a3c5fed"),
        "WI-1000XM2" to 0x01 to Art("6486bef9-9ebb-4223-97f9-bea90a3c5fed"),
        "WI-1000XM2" to 0x03 to Art("65963067-6c91-4241-a0ab-3cf95b6b8802"),
        "WH-H910N" to 0x00 to Art("1a780a62-74bf-4754-b6a4-dfe7f2c2c19c"),
        "WH-H910N" to 0x01 to Art("1a780a62-74bf-4754-b6a4-dfe7f2c2c19c"),
        "WH-H910N" to 0x04 to Art("eecb8a0a-7fe7-477d-9d4e-fe914ae31cdb"),
        "WH-H910N" to 0x05 to Art("322e4080-5aa8-4cda-a110-8419226cdddf"),
        "WH-H910N" to 0x08 to Art("984392e2-55a2-458c-b752-6f9dfbfefcc7"),
        "WH-H910N" to 0x0C to Art("b8a93a51-c575-43b4-9e7d-543f9e3aa5b5"),
        "WH-XB900N" to 0x00 to Art("899244b6-aee4-4d87-b287-fae964dba1f1"),
        "WH-XB900N" to 0x01 to Art("899244b6-aee4-4d87-b287-fae964dba1f1"),
        "WH-XB900N" to 0x05 to Art("29edc443-8e5d-44bc-b0e7-a1093a9d484c"),
        "WH-XB900N" to 0x09 to Art("3ffae6bd-790f-4e46-9287-e4256d673b8d"),
        // Not in the cloud list: the product shots from Sony's own shop page (sony.sk, WHCH710NB/NW/NL.CE7).
        "WH-CH710N" to 0x00 to Art("$SONY_SK" + "4de2bf0af81c40d26525da498670aa90$SONY_SK_SIZE"),
        "WH-CH710N" to 0x01 to Art("$SONY_SK" + "4de2bf0af81c40d26525da498670aa90$SONY_SK_SIZE"),
        "WH-CH710N" to 0x02 to Art("$SONY_SK" + "d24aa3b94803059ef4f3b2deb54d48d7$SONY_SK_SIZE"),
        "WH-CH710N" to 0x05 to Art("$SONY_SK" + "9b78fb3416b7ff8f0e7f2e773f5ce3d7$SONY_SK_SIZE"),
        // MDR v2 (same query, 2026-10-21).
        "WH-1000XM6" to 0x00 to Art("d02be2aa-7fc9-4a98-88ea-ec04984bf361"),
        "WH-1000XM6" to 0x01 to Art("d02be2aa-7fc9-4a98-88ea-ec04984bf361"),
        "WH-1000XM6" to 0x03 to Art("0ab368f2-cb51-4ce1-bb8b-9853efbd57ed"),
        "WH-1000XM6" to 0x05 to Art("87744c8f-92d9-4f26-9f2f-5c88d37f9b86"),
        "WH-1000XM6" to 0x06 to Art("781076da-9f9a-4604-9031-d69392fe5429"),
        "WH-1000XM6" to 0x08 to Art("e21bed92-619f-4275-845c-0b9c8108198e"),
        "WH-1000XM6" to 0x0D to Art("2d906990-8623-4a79-be4b-5b493879c993"),
        "WH-1000XM5" to 0x00 to Art("4e2e1840-5028-461c-bacf-26e8b2bfc2a7"),
        "WH-1000XM5" to 0x01 to Art("4e2e1840-5028-461c-bacf-26e8b2bfc2a7"),
        "WH-1000XM5" to 0x03 to Art("3335266d-ede0-4431-a2cf-282a0b38a47a"),
        "WH-1000XM5" to 0x05 to Art("0aff6c4e-b47d-479b-8ed0-096093d685e0"),
        "WH-1000XM5" to 0x06 to Art("228d02e3-58fe-4158-9611-b38a919897db"),
        "WF-1000XM6" to 0x00 to Art("428e2f01-b043-48aa-96bc-01a924dbe531"),
        "WF-1000XM6" to 0x01 to Art("428e2f01-b043-48aa-96bc-01a924dbe531"),
        "WF-1000XM6" to 0x03 to Art("d66b5c08-ef4e-4929-8025-ab63d7cf3bf7"),
        "WF-1000XM5" to 0x00 to Art("b195e9ac-6901-442f-8dc9-e0d32c1de12d", "4e2c96c7-5912-4ddc-a31f-49a936fb7071", "6fcb5e8b-fbe6-4700-970c-f9304c88436c", "3bfaaa9c-85d3-4ed9-9cf1-951350fd2dac"),
        "WF-1000XM5" to 0x01 to Art("b195e9ac-6901-442f-8dc9-e0d32c1de12d", "4e2c96c7-5912-4ddc-a31f-49a936fb7071", "6fcb5e8b-fbe6-4700-970c-f9304c88436c", "3bfaaa9c-85d3-4ed9-9cf1-951350fd2dac"),
        "WF-1000XM5" to 0x03 to Art("ad387762-be02-4acc-a49b-819bb1e69771", "468d7825-5578-472b-ad43-e27135231c7a", "782f1fdd-2870-4fe3-b581-ae2cc6130d49", "3ae39862-fd01-47d1-8b57-9ac3a86deddd"),
        "WF-1000XM5" to 0x06 to Art("338d6904-db3c-4cbe-87ed-d05f61b83399", "96272936-e2a6-4e80-9c9b-4031da4271fb", "290d7925-58f9-4928-9266-75538afeef69", "81e37b41-acc5-4f64-8481-1ab11f0226fc"),
        "WF-1000XM4" to 0x00 to Art("91924b74-3e6a-43b4-9655-08020d513951", "5cfa6a7e-5d8c-418f-8b81-6f312803f879", "46550f0b-ce05-45c4-9021-ca51dec5fbd4", "45627685-e9b7-4c4e-a001-e26d928f6c15"),
        "WF-1000XM4" to 0x01 to Art("91924b74-3e6a-43b4-9655-08020d513951", "5cfa6a7e-5d8c-418f-8b81-6f312803f879", "46550f0b-ce05-45c4-9021-ca51dec5fbd4", "45627685-e9b7-4c4e-a001-e26d928f6c15"),
        "WF-1000XM4" to 0x03 to Art("b18c4427-ca71-4d3f-84f4-e328dc002ce3", "c70ec6c0-7946-4ce2-a422-2a1d4fbf77c6", "e4eeddc1-6263-472b-a033-fbf17264c775", "4efe142f-e139-4449-b725-cf41c493ec8a"),
        "LinkBuds Fit" to 0x00 to Art("2fb83182-4ac1-432f-8b59-74520ee2611e"),
        "LinkBuds Fit" to 0x01 to Art("2fb83182-4ac1-432f-8b59-74520ee2611e"),
        "LinkBuds Fit" to 0x02 to Art("0c85f05d-0e70-4762-84c2-bca24a32726e"),
        "LinkBuds Fit" to 0x06 to Art("ac298b91-4e74-4275-841c-688eda0c87fe"),
        "LinkBuds Fit" to 0x08 to Art("9795b46c-9283-4a6a-970c-ea66a525b4d3"),
        "LinkBuds Fit" to 0x0E to Art("7de976af-ebfa-4d45-b178-c69ec4bd394b"),
        "LinkBuds Open" to 0x00 to Art("eeb73a46-5f33-4999-8bb9-818db1ef17b7"),
        "LinkBuds Open" to 0x01 to Art("eeb73a46-5f33-4999-8bb9-818db1ef17b7"),
        "LinkBuds Open" to 0x02 to Art("c72fb61e-dbee-42a8-9d7d-55993a0a72a3"),
        "LinkBuds Open" to 0x0E to Art("aceb1a29-700d-458c-8be3-e21968ca69a4"),
        "LinkBuds Clip" to 0x00 to Art("264be38a-7367-4cfd-812e-50115663fa5b"),
        "LinkBuds Clip" to 0x01 to Art("264be38a-7367-4cfd-812e-50115663fa5b"),
        "LinkBuds Clip" to 0x08 to Art("99ae0a00-fe28-425a-90f0-4599ddecab5f"),
        "LinkBuds Clip" to 0x0A to Art("d5e4fc47-f1a3-45fc-ac44-259bc77a0c1f"),
        "LinkBuds Clip" to 0x0B to Art("2fc3cc62-a922-4b0c-b332-a11d677eba47"),
        "LinkBuds Clip" to 0x0E to Art("8029e3d1-c8f5-4934-a65d-f5bfb9ba5335"),
        "LinkBuds S" to 0x00 to Art("c3e8e3f3-e66f-4725-8f20-65fedcdb0e30", "2b5fd34f-a719-48e8-82fe-e9527da0aba2", "10da037c-f4b2-45bd-b926-72122b82499d", "c007da96-4184-48e7-b78e-dbce6a6c987e"),
        "LinkBuds S" to 0x01 to Art("c3e8e3f3-e66f-4725-8f20-65fedcdb0e30", "2b5fd34f-a719-48e8-82fe-e9527da0aba2", "10da037c-f4b2-45bd-b926-72122b82499d", "c007da96-4184-48e7-b78e-dbce6a6c987e"),
        "LinkBuds S" to 0x02 to Art("686e7f02-fb62-45ed-a9d2-6040be3779be", "bdf3b611-2f53-4ebb-8fc0-32bef9dc2d6f", "a7f3e8b2-4590-41a4-b159-c23263a127be", "92231ec0-4586-4b62-aad2-772290f03b73"),
        "LinkBuds S" to 0x05 to Art("76e4e03e-b1ce-4449-b9d5-1ebe86db9371", "e9980da3-991c-4fde-b805-71fad4a8d38c", "121b94b9-81fd-4adb-9b8f-fa222451ac30", "b150bbbb-7f17-4d2d-b69a-2c46aa0346b8"),
        "LinkBuds S" to 0x0B to Art("4eec68c7-6653-4c5a-8f90-5c9a8569ef69", "164ca206-8c76-4451-b5cf-ef06360325f9", "b754cf8f-51fb-4962-b1ca-61e295915ef1", "858d7985-9639-4648-94e5-433bdda7424d"),
        "LinkBuds S" to 0x0E to Art("b4254dbd-f991-4c40-a46b-e8f3cc7fd5c5", "54dc74f8-e7be-4618-9dc2-3044906c3c5e", "8344be5e-0dac-4dee-9ca9-840c4133a5ab", "547ea351-d4f3-45b9-9099-1f72cf225b7d"),
        "LinkBuds" to 0x00 to Art("2e309fdb-c274-43d6-850b-35b269807504", "31654c3b-9b56-4f31-9407-17ea00d9f4f7", "bbd96d80-75a7-45df-b6cd-6941b4f3604b", "62fe3cb2-3a31-41e1-b293-d29655156b84"),
        "LinkBuds" to 0x02 to Art("2e309fdb-c274-43d6-850b-35b269807504", "31654c3b-9b56-4f31-9407-17ea00d9f4f7", "bbd96d80-75a7-45df-b6cd-6941b4f3604b", "62fe3cb2-3a31-41e1-b293-d29655156b84"),
        "LinkBuds" to 0x09 to Art("0db301f1-1a55-41d3-a255-08dad829f0a8", "c9da9d5d-586f-4664-97c0-4db637f5c624", "e4d21d16-e764-4f26-b8f7-c1c253f289cc", "d2fa273c-cb12-4dbc-a2cf-124a0e79d5d9"),
        "ULT WEAR" to 0x00 to Art("a2c9308f-44db-4f22-96b2-d85f1df8821a"),
        "ULT WEAR" to 0x01 to Art("a2c9308f-44db-4f22-96b2-d85f1df8821a"),
        "ULT WEAR" to 0x02 to Art("66f174b8-4680-4fd3-99f5-73db83a628b2"),
        "ULT WEAR" to 0x09 to Art("6c4af94e-7ade-4c59-9959-81d4c5dc9dae"),
        "WH-CH720N" to 0x00 to Art("c26f383f-79e9-4b97-84a3-29bb4007598d"),
        "WH-CH720N" to 0x01 to Art("c26f383f-79e9-4b97-84a3-29bb4007598d"),
        "WH-CH720N" to 0x02 to Art("a7b7dd05-0db7-49cd-bac2-66e3b4aef73a"),
        "WH-CH720N" to 0x05 to Art("5bd19958-cede-43f8-a0ea-27a9527dfd6c"),
        "WH-CH720N" to 0x06 to Art("7d72b6d5-6f48-481b-89c8-7d9f0f7a09d4"),
        "WH-CH520" to 0x00 to Art("2a5c0a58-65fe-4039-b281-232b7415dc43"),
        "WH-CH520" to 0x01 to Art("2a5c0a58-65fe-4039-b281-232b7415dc43"),
        "WH-CH520" to 0x02 to Art("42f72125-7fb7-48ae-b3f1-a48bb81737de"),
        "WH-CH520" to 0x05 to Art("3944a2df-4ca9-4b78-92a2-0ae78121685f"),
        "WH-CH520" to 0x06 to Art("bb04b6c0-c5a7-429e-afad-217c6402cbde"),
        "WH-CH520" to 0x07 to Art("b6068258-acff-4e36-a5c3-47176752e58c"),
        "WH-CH520" to 0x0B to Art("84e1a8a6-0174-4ab7-84fa-496175d5f9e5"),
        "WH-XB910N" to 0x00 to Art("078b29ea-61e8-4c9c-a11f-e4eadb792036"),
        "WH-XB910N" to 0x01 to Art("078b29ea-61e8-4c9c-a11f-e4eadb792036"),
        "WH-XB910N" to 0x05 to Art("88bb9af8-b0de-495a-8c58-44da07c836eb"),
        "WH-XB910N" to 0x09 to Art("f1bdd893-0e2c-4f0d-a1da-6d688e3f2325"),
        "WF-C710N" to 0x00 to Art("7e1a6abd-b1f0-4504-a871-998e980c5b00", "aeb9b6df-338b-4e31-970f-a828b23f3a66", "5eaeaba1-c765-4c43-8549-f40ceab1bcae", "d4f4d170-3c18-4bcb-b76c-d50e17cd44fc"),
        "WF-C710N" to 0x01 to Art("7e1a6abd-b1f0-4504-a871-998e980c5b00", "aeb9b6df-338b-4e31-970f-a828b23f3a66", "5eaeaba1-c765-4c43-8549-f40ceab1bcae", "d4f4d170-3c18-4bcb-b76c-d50e17cd44fc"),
        "WF-C710N" to 0x02 to Art("86df950f-e51a-4526-9d1f-d80338cee459", "e6f238b5-2e89-4bb8-8e0a-86e8be7f1c27", "a5eb106b-e987-445e-bc55-e20199554743", "71b445ae-ffde-4efa-8228-e6b62b5530e5"),
        "WF-C710N" to 0x05 to Art("27f47a6e-5b1d-40bd-b236-5594d0396db7", "423b915b-e516-468f-8b08-5d78234e3438", "8a37de05-1138-4bca-9fe5-f9c50a89ca29", "6585f4bc-5333-4379-afe9-942eab6c171e"),
        "WF-C710N" to 0x06 to Art("1cfb67fa-ac98-4de9-bf1d-7963a8ee9739", "289388d1-ff34-46b6-880b-73d808ae0a99", "7394dc6f-e67a-4a85-a9f8-8359664ccd5e", "1866a69a-de19-4148-baae-2576c0147fa8"),
        "WF-C700N" to 0x00 to Art("01701f9a-56b8-4c8c-aa63-671700d12c90", "5349dd6e-831c-45bf-b8b4-7ca9fbfd076d", "f8d8d962-4960-4494-9c5d-af5f47f9f718", "2ef8a7a3-0cbd-401d-837e-7c7378de1c07"),
        "WF-C700N" to 0x01 to Art("01701f9a-56b8-4c8c-aa63-671700d12c90", "5349dd6e-831c-45bf-b8b4-7ca9fbfd076d", "f8d8d962-4960-4494-9c5d-af5f47f9f718", "2ef8a7a3-0cbd-401d-837e-7c7378de1c07"),
        "WF-C700N" to 0x02 to Art("6e9048db-0915-4b15-ab44-ff2bd0b6747f", "bf6a638c-9141-49ce-aac9-363197e4da36", "8cfdf774-5fa9-4037-88c3-bd1bb745dfb5", "d1a0aa2a-45aa-43d4-a5d5-12e31e0ef2f9"),
        "WF-C700N" to 0x08 to Art("efe03989-592b-4473-9905-c0bb5825f9d4", "729b547b-9ef7-4f2e-bbe1-bb89105c574f", "62319257-3f83-4f2a-9aac-48a7ed20b4a2", "ef597b80-5228-4cdb-842f-1923b3ce383a"),
        "WF-C700N" to 0x0E to Art("8d025ac4-8b99-4ac6-8e0f-eee94dbc18ae", "d4db7839-543d-4ddd-a5fe-54b18d6406b8", "e0767209-90ed-496d-8c1a-9a04118cfddf", "f022e3a6-da35-4332-988e-d733621d73cb"),
        "WF-C510" to 0x00 to Art("ec4790f5-ed06-4a7f-bb44-646a3139a237", "f3927673-ca6d-48ad-8bda-26c5ce07a02e", "81f4c3ee-781b-4d40-9633-5533c2dec148", "9fc76716-18c3-4ce9-8d3d-af5d687f3407"),
        "WF-C510" to 0x01 to Art("ec4790f5-ed06-4a7f-bb44-646a3139a237", "f3927673-ca6d-48ad-8bda-26c5ce07a02e", "81f4c3ee-781b-4d40-9633-5533c2dec148", "9fc76716-18c3-4ce9-8d3d-af5d687f3407"),
        "WF-C510" to 0x02 to Art("10bda0d3-45af-47c5-a7e0-ee5cafa3d23a", "2c3fc6ee-e97e-49f8-9c01-a769849ad2f3", "e625a1dd-bed1-47a0-a78c-d78b44562361", "c5fb0ef9-d19d-40dc-8767-57e9475a0df4"),
        "WF-C510" to 0x05 to Art("13f82464-133c-465f-837d-54426a31ddd0", "d9fcf838-a620-4293-aead-49de15beb608", "7965329b-2d17-4b4f-b1e9-edb34a5e37ca", "15407bab-7c07-401f-8114-464694c6dc31"),
        "WF-C510" to 0x07 to Art("42f634a9-0aad-474c-a55b-7f1ca5e3f5fb", "64c8d252-a851-4a7f-bddd-5622a972d16e", "a3b63e29-6aec-404e-af49-dfabfcf58683", "f595752d-c000-42b2-b446-c27278c226be"),
    )

    /** The headset's own colour, else the model's default colour; null for models the list doesn't have. */
    fun of(model: String?, color: Int?): Pair<String, Art>? {
        val name = model?.let { m -> table.keys.map { it.first }.firstOrNull { m.contains(it, ignoreCase = true) } } ?: return null
        val c = color?.takeIf { (name to it) in table } ?: table.keys.filter { it.first == name }.minOf { it.second }
        return "sony-${name.lowercase()}-$c" to table.getValue(name to c)
    }

    private fun url(art: Art, part: Part) = when (part) {
        Part.RENDER -> art.render
        Part.LEFT -> art.left
        Part.RIGHT -> art.right
        Part.CASE -> art.case
    }?.let { if (it.startsWith("https://")) it else "$BASE$it.png" }

    private fun key(key: String, part: Part) = if (part == Part.RENDER) key else "$key-${part.name.lowercase()}"

    /** The default-colour render, for the add-device page. */
    fun urlFor(model: String): String? = of(model, null)?.let { url(it.second, Part.RENDER) }

    fun cached(context: Context, model: String?, color: Int?, part: Part = Part.RENDER): Bitmap? =
        of(model, color)?.let { (k, _) -> RemoteRender.cached(context, key(k, part), trim = true) }

    suspend fun load(context: Context, model: String?, color: Int?, part: Part = Part.RENDER): Bitmap? {
        val (k, art) = of(model, color) ?: return null
        return url(art, part)?.let { RemoteRender.load(context, key(k, part), it, trim = true) }
    }
}
