/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.ui.setup

import android.content.Context
import at.bitfire.dav4android.DavResource
import at.bitfire.dav4android.UrlUtils
import at.bitfire.dav4android.exception.DavException
import at.bitfire.dav4android.exception.HttpException
import at.bitfire.dav4android.property.*
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.log.StringHandler
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.davdroid.model.Credentials
import at.bitfire.davdroid.settings.Settings
import okhttp3.HttpUrl
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import org.apache.commons.lang3.builder.ToStringBuilder
import org.xbill.DNS.Lookup
import org.xbill.DNS.Type
import java.io.IOException
import java.io.Serializable
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class DavResourceFinder(
        val context: Context,
        private val loginInfo: LoginInfo
): AutoCloseable {

    enum class Service(val wellKnownName: String) {
        CALDAV("caldav"),
        CARDDAV("carddav");

        override fun toString() = wellKnownName
    }

    val log = Logger.getLogger("davdroid.DavResourceFinder")!!
    private val logBuffer = StringHandler()
    init {
        log.level = Level.FINEST
        log.addHandler(logBuffer)
    }

    private val settings = Settings.getInstance(context)
    private val httpClient: HttpClient = HttpClient.Builder(context, settings, logger = log)
            .addAuthentication(null, loginInfo.credentials)
            .setForeground(true)
            .build()

    fun cancel() {
        log.warning("Shutting down resource detection")
        httpClient.okHttpClient.dispatcher().executorService().shutdown()
        httpClient.okHttpClient.connectionPool().evictAll()
    }

    override fun close() {
        settings?.close()
        httpClient.close()
    }


    fun findInitialConfiguration(): Configuration {
        val cardDavConfig = findInitialConfiguration(Service.CARDDAV)
        val calDavConfig = findInitialConfiguration(Service.CALDAV)

        return Configuration(
                loginInfo.credentials,
                cardDavConfig, calDavConfig,
                logBuffer.toString()
        )
    }

    private fun findInitialConfiguration(service: Service): Configuration.ServiceInfo? {
        // user-given base URI (either mailto: URI or http(s):// URL)
        val baseURI = loginInfo.uri

        // domain for service discovery
        var discoveryFQDN: String? = null

        // put discovered information here
        val config = Configuration.ServiceInfo()
        log.info("Finding initial ${service.wellKnownName} service configuration")

        if (baseURI.scheme.equals("http", true) || baseURI.scheme.equals("https", true)) {
            HttpUrl.get(baseURI)?.let { baseURL ->
                // remember domain for service discovery
                // try service discovery only for https:// URLs because only secure service discovery is implemented
                if (baseURL.scheme().equals("https", true))
                    discoveryFQDN = baseURL.host()

                checkUserGivenURL(baseURL, service, config)

                if (config.principal == null)
                    try {
                        config.principal = getCurrentUserPrincipal(baseURL.resolve("/.well-known/" + service.wellKnownName)!!, service)
                    } catch(e: Exception) {
                        log.log(Level.FINE, "Well-known URL detection failed", e)
                    }
            }
        } else if (baseURI.scheme.equals("mailto", true)) {
            val mailbox = baseURI.schemeSpecificPart

            val posAt = mailbox.lastIndexOf("@")
            if (posAt != -1)
                discoveryFQDN = mailbox.substring(posAt + 1)
        }

        // Step 2: If user-given URL didn't reveal a principal, search for it: SERVICE DISCOVERY
        if (config.principal == null)
            discoveryFQDN?.let {
                log.info("No principal found at user-given URL, trying to discover")
                try {
                    config.principal = discoverPrincipalUrl(it, service)
                } catch(e: Exception) {
                    log.log(Level.FINE, "$service service discovery failed", e)
                }
            }

        if (config.principal != null && service == Service.CALDAV) {
            // query email address (CalDAV scheduling: calendar-user-address-set)
            val davPrincipal = DavResource(httpClient.okHttpClient, HttpUrl.get(config.principal)!!, log)
            try {
                davPrincipal.propfind(0, CalendarUserAddressSet.NAME)
                davPrincipal.properties[CalendarUserAddressSet::class.java]?.let { addressSet ->
                    for (href in addressSet.hrefs)
                        try {
                            val uri = URI(href)
                            if (uri.scheme.equals("mailto", true))
                                config.email = uri.schemeSpecificPart
                        } catch(e: URISyntaxException) {
                            log.log(Level.WARNING, "Unparseable user address", e)
                        }
                }
            } catch(e: Exception) {
                log.log(Level.WARNING, "Couldn't query user email address", e)
            }
        }

        // return config or null if config doesn't contain useful information
        val serviceAvailable = config.principal != null || config.homeSets.isNotEmpty() || config.collections.isNotEmpty()
        return if (serviceAvailable)
            config
        else
            null
    }

    private fun checkUserGivenURL(baseURL: HttpUrl, service: Service, config: Configuration.ServiceInfo) {
        log.info("Checking user-given URL: $baseURL")

        var principal: HttpUrl? = null
        try {
            val davBase = DavResource(httpClient.okHttpClient, baseURL, log)

            when (service) {
                Service.CARDDAV -> {
                    davBase.propfind(0,
                            ResourceType.NAME, DisplayName.NAME, AddressbookDescription.NAME,
                            AddressbookHomeSet.NAME,
                            CurrentUserPrincipal.NAME
                    )
                    rememberIfAddressBookOrHomeset(davBase, config)
                }
                Service.CALDAV -> {
                    davBase.propfind(0,
                            ResourceType.NAME, DisplayName.NAME, CalendarColor.NAME, CalendarDescription.NAME, CalendarTimezone.NAME, CurrentUserPrivilegeSet.NAME, SupportedCalendarComponentSet.NAME,
                            CalendarHomeSet.NAME,
                            CurrentUserPrincipal.NAME
                    )
                    rememberIfCalendarOrHomeset(davBase, config)
                }
            }

            // check for current-user-principal
            davBase.findProperty(CurrentUserPrincipal::class.java)?.let { (dav, currentUserPrincipal) ->
                currentUserPrincipal.href?.let {
                    principal = dav.location.resolve(it)
                }
            }

            // check for resource type "principal"
            if (principal == null)
                for ((dav, resourceType) in davBase.findProperties(ResourceType::class.java)) {
                    if (resourceType.types.contains(ResourceType.PRINCIPAL)) {
                        principal = dav.location
                        break
                    }
                }

            // If a principal has been detected successfully, ensure that it provides the required service.
            principal?.let {
                if (providesService(it, service))
                    config.principal = it.uri()
            }
        } catch(e: Exception) {
            log.log(Level.FINE, "PROPFIND/OPTIONS on user-given URL failed", e)
        }
    }

    /**
     * If #dav references an address book or an address book home set, it will added to
     * config.collections or config.homesets. Only evaluates already known properties,
     * does not call dav.propfind()! URLs will be stored with trailing "/".
     * @param dav       resource whose properties are evaluated
     * @param config    structure where the address book (collection) and/or home set is stored into (if found)
     */
    fun rememberIfAddressBookOrHomeset(dav: DavResource, config: Configuration.ServiceInfo) {
        // Is there an address book?
        for ((addressBook, resourceType) in dav.findProperties(ResourceType::class.java)) {
            if (resourceType.types.contains(ResourceType.ADDRESSBOOK)) {
                addressBook.location = UrlUtils.withTrailingSlash(addressBook.location)
                log.info("Found address book at ${addressBook.location}")
                config.collections.put(addressBook.location.uri(), CollectionInfo(addressBook))
            }
        }

        // Is there an addressbook-home-set?
        for ((dav, homeSet) in dav.findProperties(AddressbookHomeSet::class.java)) {
            for (href in homeSet.hrefs) {
                dav.location.resolve(href)?.let {
                    val location = UrlUtils.withTrailingSlash(it)
                    log.info("Found address book home-set at $location")
                    config.homeSets.add(location.uri())
                }
            }
        }
    }

    private fun rememberIfCalendarOrHomeset(dav: DavResource, config: Configuration.ServiceInfo) {
        // Is the collection a calendar collection?
        for ((calendar, resourceType) in dav.findProperties(ResourceType::class.java)) {
            if (resourceType.types.contains(ResourceType.CALENDAR)) {
                calendar.location = UrlUtils.withTrailingSlash(calendar.location)
                log.info("Found calendar at ${calendar.location}")
                config.collections.put(calendar.location.uri(), CollectionInfo(calendar))
            }
        }

        // Is there an calendar-home-set?
        for ((dav, homeSet) in dav.findProperties(CalendarHomeSet::class.java)) {
            for (href in homeSet.hrefs) {
                dav.location.resolve(href)?.let {
                    val location = UrlUtils.withTrailingSlash(it)
                    log.info("Found calendar home-set at $location")
                    config.homeSets.add(location.uri())
                }
            }
        }
    }


    @Throws(IOException::class)
    fun providesService(url: HttpUrl, service: Service): Boolean {
        val davPrincipal = DavResource(httpClient.okHttpClient, url, log)
        try {
            davPrincipal.options()

            if ((service == Service.CARDDAV && davPrincipal.capabilities.contains("addressbook")) ||
                (service == Service.CALDAV && davPrincipal.capabilities.contains("calendar-access")))
                return true

        } catch(e: Exception) {
            log.log(Level.SEVERE, "Couldn't detect services on " + url, e)
            if (e !is HttpException && e !is DavException)
                throw e
        }
        return false
    }


    /**
     * Try to find the principal URL by performing service discovery on a given domain name.
     * Only secure services (caldavs, carddavs) will be discovered!
     * @param domain         domain name, e.g. "icloud.com"
     * @param service        service to discover (CALDAV or CARDDAV)
     * @return principal URL, or null if none found
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    private fun discoverPrincipalUrl(domain: String, service: Service): URI? {
        val scheme: String
        val fqdn: String
        var port = 443
        val paths = LinkedList<String>()     // there may be multiple paths to try

        val query = "_${service.wellKnownName}s._tcp.$domain"
        log.fine("Looking up SRV records for $query")
        val srvLookup = Lookup(query, Type.SRV)
        DavUtils.prepareLookup(context, srvLookup)
        val srv = DavUtils.selectSRVRecord(srvLookup.run())
        if (srv != null) {
            // choose SRV record to use (query may return multiple SRV records)
            scheme = "https"
            fqdn = srv.target.toString(true)
            port = srv.port
            log.info("Found $service service at https://$fqdn:$port")
        } else {
            // no SRV records, try domain name as FQDN
            log.info("Didn't find $service service, trying at https://$domain:$port")

            scheme = "https"
            fqdn = domain
        }

        // look for TXT record too (for initial context path)
        val txtLookup = Lookup(query, Type.TXT)
        DavUtils.prepareLookup(context, txtLookup)
        paths.addAll(DavUtils.pathsFromTXTRecords(txtLookup.run()))

        // if there's TXT record and if it it's wrong, try well-known
        paths.add("/.well-known/" + service.wellKnownName)
        // if this fails, too, try "/"
        paths.add("/")

        for (path in paths)
            try {
                val initialContextPath = HttpUrl.Builder()
                        .scheme(scheme)
                        .host(fqdn).port(port)
                        .encodedPath(path)
                        .build()

                log.info("Trying to determine principal from initial context path=$initialContextPath")
                val principal = getCurrentUserPrincipal(initialContextPath, service)

                principal?.let { return it }
            } catch(e: Exception) {
                log.log(Level.WARNING, "No resource found", e)
            }
        return null
    }

    /**
     * Queries a given URL for current-user-principal
     * @param url       URL to query with PROPFIND (Depth: 0)
     * @param service   required service (may be null, in which case no service check is done)
     * @return          current-user-principal URL that provides required service, or null if none
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    fun getCurrentUserPrincipal(url: HttpUrl, service: Service?): URI? {
        val dav = DavResource(httpClient.okHttpClient, url, log)
        dav.propfind(0, CurrentUserPrincipal.NAME)

        dav.findProperty(CurrentUserPrincipal::class.java)?.let { (dav, currentUserPrincipal) ->
            currentUserPrincipal.href?.let { href ->
                dav.location.resolve(href)?.let { principal ->
                    log.info("Found current-user-principal: $principal")

                    // service check
                    if (service != null && !providesService(principal, service)) {
                        log.info("$principal doesn't provide required $service service")
                        return null
                    }

                    return principal.uri()
                }
            }
        }
        return null
    }


    // data classes

    class Configuration(
            val credentials: Credentials,

            val cardDAV: ServiceInfo?,
            val calDAV: ServiceInfo?,

            val logs: String
    ): Serializable {
        // We have to use URI here because HttpUrl is not serializable!

        class ServiceInfo: Serializable {
            var principal: URI? = null
            val homeSets = HashSet<URI>()
            val collections = HashMap<URI, CollectionInfo>()

            var email: String? = null

            override fun toString() = ToStringBuilder.reflectionToString(this)!!
        }

        override fun toString(): String {
            val builder = ReflectionToStringBuilder(this)
            builder.setExcludeFieldNames("logs")
            return builder.toString()
        }

    }

}
