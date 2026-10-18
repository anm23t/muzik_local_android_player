# 004. Hilt for dependency injection

Status: accepted

Hilt wires modules together in `app` while `core:*` and `feature:*` only see interfaces from `core:model`.
It removes manual factories, gives ViewModels injection out of the box and lets tests replace
implementations (for example dispatchers). The cost is annotation processing (KSP) at build time.
