= Idea

* Create an App that helps me store my notes, kilometer and other things on longer cycling and hiking trips
* it should work without internet but support syncing. i thought to use git as local storage, and add support to sync with remote (push / pull) into the app.
*it should run on macos, linux and java so i was thinking of simple java spring boot app with a gui, since the jvm can run anyhwere. not sure what guis are en vogue theses days, i have java 25 everyhwere, and maven should be used as build system. init as git repo.
* data model is: 1 trip, one diary entry per day  identified by year/month/date, and you can scroll through the days.
* the trip should be a slug without blanks, special chars etc also used in the storage as top identifier for the trip, currently immutable. 
* the diary entries should hold notes for text, km and atitude per day (maybe more in future). the internal format on the filesystem is up to you, but it should be human readable, so I suggest simple markdown and some structure for the fields
That's all for the PoC, ask me if anything is unclear
